// configuration for plugin testing - will not be included in the plugin zip

log4j = {
    // Example of changing the log pattern for the default console
    // appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
           'org.codehaus.groovy.grails.web.pages', //  GSP
           'org.codehaus.groovy.grails.web.sitemesh', //  layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping', // URL mapping
           'org.codehaus.groovy.grails.commons', // core / classloading
           'org.codehaus.groovy.grails.plugins', // plugins
           'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'
}

grails {  // example of configuration
    redis {
        poolConfig {
            // jedis pool specific tweaks here, see jedis docs & src
            // ex: testWhileIdle = true
        }
        database = 1          // each other grails env can have a private one
        port = 6379
        host = 'localhost'
        timeout = 2000 // default in milliseconds
        password = '' // defaults to no password

        cache {
            //enabled = false // cache enabled by default
            database = 2
            host = 'localhost'  // will override the base one
            defaultTTL = 10 * 60 // seconds (used only if no ttl are declared in the annotation/map and no expireMap is defined
            expireMap = [never: Integer.MAX_VALUE, //values in seconds
                    low: 10 * 60,
                    mid_low: 5 * 60,
                    mid: 2 * 60,
                    high: 1 * 60
            ]

        }
    }
}