grails.project.work.dir = 'target'

grails.project.dependency.resolution = {

    inherits 'global'
    log 'warn'

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
    }

    dependencies {
    }

    plugins {
        build ':release:3.0.1', ':rest-client-builder:2.0.0', {
            export = false
        }

        // redis support
        compile ':redis:1.4.3'
    }
}
