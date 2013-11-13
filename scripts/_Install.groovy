/*
 * Copyright 2006-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


private void updateConfig() {
    def appDir = "$basedir/grails-app"
    def configFile = new File(appDir, 'conf/Config.groovy')
    if (configFile.exists()) {
        configFile.withWriterAppend {
            it.writeLine " grails.redisflexiblecache.connectiontouse = 'cache'                                                                                "
            it.writeLine " grails {  // example of configuration                                                                                              "
            it.writeLine "     redis {                                                                                                                        "
            it.writeLine "         poolConfig {                                                                                                               "
            it.writeLine "             // jedis pool specific tweaks here, see jedis docs & src                                                               "
            it.writeLine "             // ex: testWhileIdle = true                                                                                            "
            it.writeLine "         }                     // base configuration                                                                                "
            it.writeLine "         database = 1                                                                                                               "
            it.writeLine "         port = 6379                                                                                                                "
            it.writeLine "         host = 'localhost'                                                                                                         "
            it.writeLine "         timeout = 2000 // default in milliseconds                                                                                  "
            it.writeLine "         password = '' // defaults to no password                                                                                   "
            it.writeLine "                                                                                                                                    "
            it.writeLine "         // not mandatory, if no cache { } block is specified redis ttl cache plugin will use the base configuration                "
            it.writeLine "         // if a property is missing in cache { } block the plugin will use the base one                                            "
            it.writeLine "         connections {                                                                                                              "
            it.writeLine "             cache {                                                                                                                "
            it.writeLine "                 //enabled = false // cache enabled by default                                                                      "
            it.writeLine "                 database = 2                                                                                                       "
            it.writeLine "                 host = 'localhost'  // will override the base one,                                                                 "
            it.writeLine "                 defaultTTL = 10 * 60 // seconds (used only if no ttl are declared in the annotation/map and no expireMap is defined"
            it.writeLine "                 expireMap = [never: Integer.MAX_VALUE, //values in seconds                                                         "
            it.writeLine "                         low: 10 * 60,                                                                                              "
            it.writeLine "                         mid_low: 5 * 60,                                                                                           "
            it.writeLine "                         mid: 2 * 60,                                                                                               "
            it.writeLine "                         high: 1 * 60                                                                                               "
            it.writeLine "                 ]                                                                                                                  "
            it.writeLine "            }                                                                                                                       "
            it.writeLine "         }                                                                                                                          "
            it.writeLine "     }                                                                                                                              "
            it.writeLine " }                                                                                                                                  "
        }
    }
}

includeTargets << grailsScript("_GrailsInit")

target(main: "Updates the Config.groovy") {
    updateConfig()
}

setDefaultTarget(main)