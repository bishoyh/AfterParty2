package afterparty

import grails.plugin.springcache.annotations.Cacheable

class StatisticsService {

    static transactional = true

    def grailsApplication
    def blastService

    static paleAssemblyColours = ['LightCyan', 'LightPink', 'LightSkyBlue']
//    public static boldAssemblyColours = ['#00FFFF', '#FFC0CB', '#87CEEB', '#8A2BE2', '#DC143C']
    public static boldAssemblyColours = ['blue','red', 'green','purple',  'fuchsia', 'grey', 'lime', 'maroon', 'navy', 'olive',  'teal', 'yellow', 'aqua']


    @Cacheable("myCache")
    def getAssemblyStats(Long id) {

        println "getting assembly stats for $id"
        def start = System.currentTimeMillis()

        if (!Assembly.get(id).defaultContigSet) {
            createContigSetForAssembly(id)
        }

        def criteria = Assembly.createCriteria()
        def a = criteria.get({
            eq('id', id)
//            fetchMode 'contigs', org.hibernate.FetchMode.JOIN
//            fetchMode 'contigs.blastHits', org.hibernate.FetchMode.JOIN
            //            fetchMode 'contigs.reads', org.hibernate.FetchMode.JOIN
        })
        println "got raw assembly object : " + (System.currentTimeMillis() - start)


        println "saved contig set : " + (System.currentTimeMillis() - start)

        def assemblyStats = grailsApplication.mainContext.statisticsService.getContigStatsForContigSet(a.defaultContigSet.id)



        int n50Total = 0
        int n50 = 0
        int n50Target = assemblyStats.length.sum() / 2
        for (contigLength in assemblyStats.length.sort().reverse()) {
            n50Total += contigLength
            if (n50Total > n50Target) {
                println "got n50 for $contigLength with $n50Total"
                n50 = contigLength
                break
            }
        }

        println "calculated n50 : " + (System.currentTimeMillis() - start)


        def result = [
                'readCount': assemblyStats.length.size(),
                'meanLength': assemblyStats.length.sum() / assemblyStats.length.size(),
                'baseCount': assemblyStats.length.sum(),
                'maxLength': assemblyStats.length.max(),
                'minLength': assemblyStats.length.min(),
                'n50': n50
        ]
        println "built return : " + (System.currentTimeMillis() - start)

        return result
    }

//    Method to generate statistics about a file of reads. We will calculate all the
    //    stats at once and return them as a map, ensuring that the results get cached
    @Cacheable("myCache")
    def getReadFileDataStats(Long id) {

        ReadsFileData f = ReadsFileData.get(id)

//        get the data
        String fileString = new String(f.fileData)
        def lines = fileString.split("\n")
        int totalBases = 0
        int readCount = 0
        int maxReadLength = 0
        int minReadLength = 10000000
        for (int i = 1; i < lines.size(); i += 4) {
            readCount++
            int readLength = lines[i].length()
            totalBases += readLength

            if (readLength > maxReadLength) {
                maxReadLength = readLength
            }
            if (readLength < minReadLength) {
                minReadLength = readLength
            }
        }

        int meanLength = totalBases / readCount
        return [
                'readCount': readCount,
                'meanLength': meanLength,
                'baseCount': totalBases,
                'maxLength': maxReadLength,
                'minLength': minReadLength,
        ]
    }



    @Cacheable("myCache")
    def getContigStatsForContigSet(Long id) {
        println "starting getContigStatsForContig"
        def start = System.currentTimeMillis()

        def criteria = ContigSet.createCriteria()
        def cs = criteria.get({
            eq('id', id)
//            fetchMode 'contigs', org.hibernate.FetchMode.JOIN
//            fetchMode 'contigs.blastHits', org.hibernate.FetchMode.JOIN
        })
        println "got $cs"
        println "got contigs : " + (System.currentTimeMillis() - start)

        def contigs = cs.contigs
        def result = [
                id: contigs*.id,
                length: contigs*.length(),
                quality: contigs*.averageQuality(),
                coverage: contigs*.averageCoverage(),
                gc: contigs*.gc(),
                topBlast: contigs.collect({
                    it.blastHits.size() > 0 ? it.blastHits.toArray()[0].description : 'no blast hit'
                })
        ]
        println "built return : " + (System.currentTimeMillis() - start)
        return result

    }

    @Cacheable("contigCache")
    def getTagCloudForAssembly(Long id) {
        def criteria = Assembly.createCriteria()
        def a = criteria.get({
            eq('id', id)
            fetchMode 'contigs', org.hibernate.FetchMode.JOIN
            fetchMode 'contigs.blastHits', org.hibernate.FetchMode.JOIN
        })

        def lines = afterparty.Contig.list().blastHits*.description.flatten()

        def word2count = [:]
        lines.collect({it.tokenize()}).flatten().findAll({it.size() > 5}).each {
            word2count.put(it, word2count.containsKey(it) ? word2count.get(it) + 1 : 1)
        }

        return word2count.sort({-it.value})
    }

    def getStatsForContigSets(List<ContigSet> contigSetList) {

        def result = []

        // figure out the buckets sizes for histograms
        def overallMaxLength = contigSetList.collect({grailsApplication.mainContext.statisticsService.getContigStatsForContigSet(it.id).length.max()}).max()     // nicely functional
        def overallMaxQuality = contigSetList.collect({grailsApplication.mainContext.statisticsService.getContigStatsForContigSet(it.id).quality.max()}).max()
        def overallMaxCoverage = contigSetList.collect({grailsApplication.mainContext.statisticsService.getContigStatsForContigSet(it.id).coverage.max()}).max()



        contigSetList.eachWithIndex {  ContigSet contigSet, index ->



            def contigSetJSON = [:]

            contigSetJSON.id = contigSet.name
            contigSetJSON.colour = StatisticsService.boldAssemblyColours[index]
            def contigStats = grailsApplication.mainContext.statisticsService.getContigStatsForContigSet(contigSet.id)

            // build a histogram of length and a scaled histogram of length
            contigSetJSON.lengthvalues = []
            contigSetJSON.scaledlengthvalues = []
            (0..overallMaxLength / 10).each {
                def floor = it * 10
                def ceiling = (it * 10) + 10
                def count = contigStats.length.findAll({it >= floor && it < ceiling}).size()
                contigSetJSON.lengthvalues.add([floor, count])
                contigSetJSON.scaledlengthvalues.add([floor, (1000 * (count / contigStats.length.size())).toInteger()])
            }

            // build a histogram of quality and a scaled histogram of length
            contigSetJSON.qualityvalues = []
            contigSetJSON.scaledqualityvalues = []
            (0..overallMaxQuality / 1).each {
                def floor = it * 1
                def ceiling = (it * 1) + 1
                def count = contigStats.quality.findAll({it >= floor && it < ceiling}).size()
                contigSetJSON.qualityvalues.add([floor, count])
                contigSetJSON.scaledqualityvalues.add([floor, (1000 * (count / contigStats.quality.size())).toInteger()])
            }

            // build a histogram of coverage and a scaled histogram of length
            contigSetJSON.coveragevalues = []
            contigSetJSON.scaledcoveragevalues = []
            (0..overallMaxCoverage / 1).each {
                def floor = it * 1
                def ceiling = (it * 1) + 1
                def count = contigStats.coverage.findAll({it >= floor && it < ceiling}).size()
                contigSetJSON.coveragevalues.add([floor, count])
                contigSetJSON.scaledcoveragevalues.add([floor, (1000 * (count / contigStats.coverage.size())).toInteger()])
            }


            result.add(contigSetJSON)
        }
        return result

    }

    def createContigSetForCompoundSample(Long id) {

        CompoundSample c = CompoundSample.get(id)

        def cs = new ContigSet(
                name: "all contigs in assemblies for $c.name",
                description: "all contigs in assemblies for $c.name",
                study: c.study,
                type: ContigSetType.COMPOUND_SAMPLE
        )

        c.assemblies.each { assembly ->
            assembly.defaultContigSet.contigs.each { contig ->
                cs.addToContigs(contig)
            }
        }

        if (c.defaultContigSet) {
            println "deleting old contig set"
            def currentDefaultContigSet = c.defaultContigSet
            currentDefaultContigSet.delete()
        }
        c.defaultContigSet = cs
        blastService.attachBlastDatabaseToContigSet(cs)
        cs.save(flush: true)

        createContigSetForStudy(c.study.id)
    }

    def createContigSetForStudy(Long id) {

        Study s = Study.get(id)

        def cs = new ContigSet(
                name: "all contigs in assemblies for study $s.name",
                description: "all contigs in assemblies for $s.name",
                study: s,
                type: ContigSetType.STUDY
        )

        s.compoundSamples.each { compoundSample ->
            compoundSample.assemblies.each { assembly ->
                assembly.defaultContigSet.contigs.each { contig ->
                    cs.addToContigs(contig)
                }
            }
        }


        if (s.defaultContigSet) {
            println "deleting old contig set"
            def currentDefaultContigSet = s.defaultContigSet
            currentDefaultContigSet.delete()
        }
        s.defaultContigSet = cs
        blastService.attachBlastDatabaseToContigSet(cs)
        cs.save(flush: true)
    }

    def createContigSetForAssembly(Long id) {
        def criteria = Assembly.createCriteria()
        def a = criteria.get({
            eq('id', id)
            fetchMode 'contigs', org.hibernate.FetchMode.JOIN
        })

        def cs = new ContigSet(
                name: "$a.name",
                description: "automatically generated contig set for $a.name",
                study: a.compoundSample.study,
                type: ContigSetType.ASSEMBLY
        )
        a.contigs.each {
            cs.addToContigs(it)
        }
        if (a.defaultContigSet) {
            println "deleting old contig set"
            def currentDefaultContigSet = a.defaultContigSet
            currentDefaultContigSet.delete()
        }
        a.defaultContigSet = cs

        blastService.attachBlastDatabaseToContigSet(cs)
        cs.save(flush: true)

        // now update the compound sample that owns this assembly
        createContigSetForCompoundSample(a.compoundSample.id)

    }
}
