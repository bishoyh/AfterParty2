package afterparty

import grails.plugins.springsecurity.Secured

class NavController {

    def springSecurityService

    def show = {
        def study = session?.studyId ? Study.get(session.studyId) : null
        [study: study ]
    }

    @Secured(['ROLE_USER'])
    def showStudies = {
        def user = AfterpartyUser.get(springSecurityService.principal.id)
        def studies = user.studies
        [studies: studies]
    }
}
