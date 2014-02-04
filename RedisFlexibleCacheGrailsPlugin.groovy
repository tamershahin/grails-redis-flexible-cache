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


import org.gametube.redisflexiblecache.RedisFlexibleCacheService
import org.gametube.redisflexiblecache.RedisFlexibleDeserializer
import org.gametube.redisflexiblecache.RedisFlexibleSerializer
import org.springframework.core.serializer.DefaultSerializer
import org.springframework.core.serializer.support.DeserializingConverter
import org.springframework.core.serializer.support.SerializingConverter

class RedisFlexibleCacheGrailsPlugin {
    def version = "0.3"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
            "grails-app/domain/**", "grails-app/views/**", "grails-app/controllers/**"
    ]

    def title = "Redis Flexible Cache Plugin"
    def author = "Tamer Shahin"
    def authorEmail = "tamer@gametube.org"
    def description = '''
This plugin is an alternative to redis-cache-plugin. It gives the possibility to set the expire time in seconds for every cached key, and provides a service, annotations and injected methods to perform entry caching and eviction.
The redis-plugin plugin also provides the possibility to set a TTL for a cached entry (using the provided @Memoize annotation), but it lacks the option to serialize any kind of Serializable objects (only object ids are cached and then hydrated from main DB).
This means that potentially a lot of data will go to redis, so pay attention to memory and bandwidth consumption!
This plugin is not an extension of cache-plugin plugin, it is far more simple and lighter at the same time. The cache-plugin gives a deep integration with grails Controller CoC mechanism, but i think it creates too much overhead sometimes.
The cache implementation provided by this plugin is inspired by redis-cache-plugin and redis-plugin but is not based on them. This plugins depends on redis-plugin for communication with redis and therefore it uses its configuration DSL.'''

    def documentation = "https://github.com/tamershahin/grails-redis-flexible-cache/blob/master/README.md"

    def license = "APACHE"

    def loadAfter = ['redis']

    def watchedResources = ['file:./grails-app/controllers/**', 'file:./grails-app/services/**']

    def organization = [name: "GameTube SAS", url: "http://www.gametube.org/"]
    def developers = [[name: "Germán Sancho", email: "german@gametube.org"]]
    def issueManagement = [system: "GITHUB", url: "https://github.com/tamershahin/grails-redis-flexible-cache/issues"]
    def scm = [url: "https://github.com/tamershahin/grails-redis-flexible-cache"]

    def doWithSpring = {

        String connectionToUse = mergeConfigMaps(application)?.connectionToUse?.capitalize()

        customSerializer(DefaultSerializer)  // the standard serializer is ok
        customDeserializer(RedisFlexibleDeserializer) // but the standard deserializer is slow, so I use custom one instead

        serializingConverter(SerializingConverter, ref('customSerializer'))
        deserializingConverter(DeserializingConverter, ref('customDeserializer'))

        redisFlexibleSerializer(RedisFlexibleSerializer) {
            serializingConverter = ref('serializingConverter')
            deserializingConverter = ref('deserializingConverter')
        }

        redisFlexibleCacheService(RedisFlexibleCacheService) {
            redisFlexibleSerializer = ref('redisFlexibleSerializer')
            redisService = ref('redisService' + connectionToUse)
            grailsApplication = ref('grailsApplication')
        }

    }

    def doWithDynamicMethods = { ctx ->
        addCacheMethodsAndLoadConfig(ctx)
    }

    def onChange = { event ->
        addCacheMethodsAndLoadConfig(event.application.mainContext)
    }

    def onConfigChange = { event ->
        addCacheMethodsAndLoadConfig(event.application.mainContext)
    }

    // If the specified connection exists, use it. If there is no connection specified use 'cache'. Otherwise use only
    // base parameters
    def mergeConfigMaps(application) {

        String connectionToUse = application.config.grails.redisflexiblecache.connectiontouse ?: ""
        def redisConfigMap = application.config.grails.redis ?: [:]

        if (!redisConfigMap.connections[connectionToUse]) {
            if (redisConfigMap.connections.cache) {
                connectionToUse = 'cache'
            } else { // if connectionToUse and cache connections are not configured don't merge nothing
                connectionToUse = ''
            }
        }

        redisConfigMap.connectionToUse = connectionToUse
        return redisConfigMap + redisConfigMap.connections[connectionToUse]

    }

    // Inject cache and evict methods in controllers and services
    def addCacheMethodsAndLoadConfig(mainContext) {

        def redisFlexibleCS = mainContext.redisFlexibleCacheService

        def redisCacheConfigMap = mergeConfigMaps(mainContext.grailsApplication)
        redisFlexibleCS.expireMap = redisCacheConfigMap?.expireMap ?: [:]
        redisFlexibleCS.defaultTTL = redisCacheConfigMap?.defaultTTL ?: 0
        redisFlexibleCS.enabled = redisCacheConfigMap?.enabled == false ?: true
        redisFlexibleCS.redisService = mainContext."redisService${redisCacheConfigMap.connectionToUse.capitalize()}"

        def clazzes = []
        clazzes += mainContext.grailsApplication.controllerClasses*.clazz
        clazzes += mainContext.grailsApplication.serviceClasses*.clazz
        clazzes.each { cls ->
            cls.metaClass.redisFlexibleCache = { Map args, Closure closure ->
                redisFlexibleCS.doCache(args.key, args.group, args.ttl, args.reAttachToSession ?: false, closure)
            }
            cls.metaClass.evictRedisFlexibleCache = { Map args, Closure closure = null ->
                redisFlexibleCS.evictCache(args.key, closure)
            }
        }
    }

}
