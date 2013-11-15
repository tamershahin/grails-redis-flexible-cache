grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {

    inherits "global"
    log "warn"

    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()
        mavenCentral()
        grailsRepo "http://grails.org/plugins"
// mavenRepo "http://m2repo.spockframework.org/snapshots"
    }

    dependencies {
//        compile 'redis.clients:jedis:2.1.0'
    }

    plugins {
        build(":release:3.0.1",
                ":rest-client-builder:1.0.3") {
            export = false
        }

        // redis support
        compile ':redis:1.3.3'
    }
}

