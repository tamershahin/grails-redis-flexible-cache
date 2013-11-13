grails.project.work.dir = 'target'

grails.project.dependency.resolver = "maven"

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        test "org.spockframework:spock-grails-support:0.7-groovy-2.0"
    }
    plugins {
        build(":release:3.0.1",
              ":rest-client-builder:1.0.3") {
            export = false
        }

        // for tests
        test(":spock:0.7") {
            exclude "spock-grails-support"
        }

        // redis support
        compile ":redis:1.3.3" //":redis:1.4.1"
    }
}

