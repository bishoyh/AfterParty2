package afterparty

class CompoundSampleFilters {

    def springSecurityService


    def filters = {
        compoundSampleExists(controller: 'compoundSample', action: '(show|createSample|createAssembly|showAssembliesJSON)') {
            before = {
                println "checking if compound sample with id ${params.id} exists"
                CompoundSample s = CompoundSample.get(params.id)
                if (!s) {
                    flash.error = "Compound sample doesn't exist"
                    redirect(controller: 'study', action: 'listPublished')
                    return false
                }
            }
        }

        compoundSampleIsPublicOrOwnedByUser(controller: 'compoundSample', action: '(show|showAssembliesJSON)') {
            before = {
                println "checking if compound sample is either public or owned"
                CompoundSample s = CompoundSample.get(params.id)
                def user = springSecurityService.isLoggedIn() ? springSecurityService?.principal : null

                if (!s.isPublished() && !s.isOwnedBy(user)) {
                    flash.error = "Compound sample is not published and you are not the owner"
                    redirect(controller: 'study', action: 'listPublished')
                    return false
                }
            }
        }

        studyIsOwnedByUser(controller: 'compoundSample', action: '(createSample|createAssembly)') {
            before = {
                println "checking if compound sample is owned by user"
                CompoundSample s = CompoundSample.get(params.id)
                if (!s.isOwnedBy(springSecurityService.principal)) {
                    flash.error = "Compound sample doesn't belong to you"
                    redirect(controller: 'compoundSample', action: 'show', id: s.id)
                    return false
                }
            }
        }

    }

}
