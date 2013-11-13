import org.gametube.dmg.core.cache.RedisFlexibleCachingService
import org.gametube.dmg.core.cache.serializer.RedisFlexibleDeserializer
import org.gametube.dmg.core.cache.serializer.RedisFlexibleSerializer
import org.springframework.core.serializer.DefaultSerializer
import org.springframework.core.serializer.support.DeserializingConverter
import org.springframework.core.serializer.support.SerializingConverter
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

//import redis.clients.jedis.JedisPool
//import redis.clients.jedis.JedisSentinelPool
class RedisFlexibleCacheGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.3 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
    ]

    def title = "Redis Flexible Cache Plugin" // Headline display name of the plugin
    def author = "Tamer Shahin"
    def authorEmail = "tamer@gametube.org"
    def description = '''\
This plugin is an alternative to redis-cache. It give the possibility to set the expire time in seconds for every cached keys.
Using the redis plugin this feature is available using the @Memoize annotation given, but the it lacks the option to serialize
any kind of Serializable object. This plugin is inspired by both but is not based on them.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/dmg-cache"

    def license = "APACHE"

    def loadAfter = ['redis']

    def watchedResources = ['file:./grails-app/controllers/**', 'file:./grails-app/services/**']

    // Details of company behind the plugin (if there is one)
    def organization = [name: "GameTube sas", url: "http://www.gametube.org/"]

    // Any additional developers beyond the author specified above.
    def developers = [[name: "Germán Sancho", email: "german@gametube.org"]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/tamershahin/Redis-Flexible-Cache" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
        def redisCacheConfigMap = mergeConfigMaps(application)
        configureService.delegate = delegate
        configureService(redisCacheConfigMap, "")
        redisCacheConfigMap?.connections?.each { connection ->
            configureService(connection.value, connection?.key?.capitalize())
        }


        customSerializer(DefaultSerializer)  //the standard serializer is ok
        customDeserializer(RedisFlexibleDeserializer) //but the standard deserializer is slow, so I use custom one instead

        serializingConverter(SerializingConverter, ref('customSerializer'))
        deserializingConverter(DeserializingConverter, ref('customDeserializer'))

        redisFlexibleSerializer(RedisFlexibleSerializer) {
            serializingConverter = ref('serializingConverter')
            deserializingConverter = ref('deserializingConverter')
        }

        redisFlexibleCachingService(RedisFlexibleCachingService) {
            redisFlexibleSerializer = ref('redisFlexibleSerializer')
            redisService = ref('redisService')
            grailsApplication = ref('grailsApplication')
        }
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
        addCacheMethods(ctx)
    }

    def doWithApplicationContext = { ctx ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
        addCacheMethods(event.application.mainContext)
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
        addCacheMethods(event.application.mainContext)
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }

    def mergeConfigMaps(def application) {
        def redisConfigMap = application.config.grails.redis ?: [:]
        redisConfigMap.merge(redisConfigMap.cache)
    }

    def addCacheMethods(def mainContext) {

        def redisFlexibleCS = mainContext.redisFlexibleCachingService

        def clazzes = []
        clazzes += mainContext.grailsApplication.controllerClasses*.clazz
        clazzes += mainContext.grailsApplication.serviceClasses*.clazz
        clazzes.each { cls ->
            cls.metaClass.cache = { Map args, Closure closure ->
                redisFlexibleCS.doCache(args.key, args.group, args.ttl, args.reAttachToSession ?: false, closure)
            }
            cls.metaClass.evictCache = { Map args, Closure closure = null ->
                redisFlexibleCS.evictCache(args.key, closure)
            }
        }

        def redisCacheConfigMap = mergeConfigMaps(mainContext.grailsApplication)
        redisFlexibleCS.expireMap = redisCacheConfigMap?.expireMap ?: [:]
        redisFlexibleCS.defaultTTL = redisCacheConfigMap?.defaultTTL ?: [:]
        redisFlexibleCS.enabled = redisCacheConfigMap?.enabled == false ?: true
    }

    /**
     * delegate to wire up the required beans.
     */
    def configureService = { redisConfigMap, key ->
        def poolBean = "redisPoolConfig${key}"
        "${poolBean}"(JedisPoolConfig) {
            // used to set arbitrary config values without calling all of them out here or requiring any of them
            // any property that can be set on RedisPoolConfig can be set here
            redisConfigMap.poolConfig.each { configkey, value ->
                delegate.setProperty(configkey, value)
            }
        }

        def host = redisConfigMap?.host ?: 'localhost'
        def port = redisConfigMap?.port ?: Protocol.DEFAULT_PORT
        def timeout = redisConfigMap?.timeout ?: Protocol.DEFAULT_TIMEOUT
        def password = redisConfigMap?.password ?: null
        def database = redisConfigMap?.database ?: Protocol.DEFAULT_DATABASE
        def sentinels = redisConfigMap?.sentinels ?: null
        def masterName = redisConfigMap?.masterName ?: null

        //TODO: remove comments when using jedis:2.2.0 instead of 2.1.0
//        // If sentinels and a masterName is present, using different pool implementation
//        if (sentinels && masterName) {
//            "redisPool${key}"(JedisSentinelPool, masterName, sentinels as Set, ref(poolBean), timeout, password, database) { bean ->
//                bean.destroyMethod = 'destroy'
//            }
//        } else {
        "redisPool${key}"(JedisPool, ref(poolBean), host, port, timeout, password, database) { bean ->
            bean.destroyMethod = 'destroy'
        }
//        }
//
////        only wire up additional services when key provided for multiple connection support
//        if (key) {
//            "redisService${key}"(RedisService) {
//                redisPool = ref("redisPool${key}")
//            }
//        }
    }
}
