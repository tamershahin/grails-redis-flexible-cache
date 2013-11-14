Grails Flexible Cache Redis Plugin
==================================

This plugin is an alternative to [redis-cache-plugin]. It gives the possibility to set the expire time in seconds for every cached key, and provides a service, annotations and injected methods to perform entry caching and eviction.
The [redis-plugin] plugin also provides the possibility to set a TTL for a cached entry (using the provided `@Memoize` annotation), but the it lacks the option to serialize any kind of Serializable objects (only object ids are cached and then hydrated from main DB). 

This plugin is not an extension of [cache-plugin] plugin, it is far more simple and lighter at the same time.
The [cache-plugin] gives a deep integration with grails Controller CoC mechanism, but i think it creates too much overhead sometimes.

This plugin is inspired by both but is not based on them.

Installation
------------
Dependency :

    compile ":redis-flexible-cache:0.1" 

In order to access the redis server where cached entries are stored, the plugin uses the configuration of the [redis-plugin]. 
Typically, you'd have something like this in your `grails-app/conf/Config.groovy` file:

    grails.redisflexiblecache.connectiontouse = 'cache'
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
            connections {
                 cache {
                     //enabled = false // cache enabled by default
                     database = 2
                     host = 'devserver'  // will override the base one
                     defaultTTL = 10 * 60 // seconds (used only if no ttl are declared in the annotation/map and no expireMap is defined
                     expireMap = [never: Integer.MAX_VALUE,
                             low: 10 * 60,
                             mid_low: 5 * 60,
                             mid: 2 * 60,
                             high: 1 * 60
                     ]

                 }
            }
        }
    }

There are two additional entries in the connection configuration:
 * `defaultTTL`: indicates the amount of seconds to use as TTL when no other TTL value is provided explicitly
 * `expireMap`: provides names and values (in seconds) for TTL presets that can be used in annotations/method calls 


Plugin Usage
------------

### redisFlexibleCacheService Bean ###

    def redisFlexibleCacheService

The `redisFlexibleCacheService` has 2 methods: 
 * `doCache`: stores/retrieves an object from the cache using a given key.
 * `evictCache` evicts a key and its value from the cache.

This service is a standard Spring bean that can be injected and used in controllers, services, etc.

Example:
    
    redisFlexibleCacheService.doCache(key, group, ttl, reattach, {
        return somethingToCache
    })

    redisFlexibleCacheService.evictCache(keys, {
                log.debug('evicted :' + keys);
    })

### Controllers/Services dynamic methods ###

At application startup/reaload each Grails controller and service gets injected the two methods provided from redisFlexibleCacheService with the following names:
 * `cache`
 * `evictCache`

Here is an example of usage:

    def index(){
      //params validation, ecc...
      def res = cache group: 'longLasting', key: "key:1", reAttachToSession: true, {
                  def bookList = // long lasting algortitm that gives back Domain Classes
                  def calculated = // long lasting algoritm that gives back Integer  
                  [book: bookList, statistics: calculated]
              }
       // ..
       return [result: res]
    }

### Annotation ###

It is also possible to use two Annotations on methods to access to cache functionality: 
 * `@RedisFlexibleCache`
 * `@EvictRedisFlexibleCache` 

Here is an example of usage:
    
    class BookService{
        @RedisFlexibleCache(key = 'bookservice:serviceMethod:author:1:book:#{text}', expire = '60',reAttachToSession = false)
        def serviceMethod(String text) {
            // long lasting operations
            return res
        }
        @EvictRedisFlexibleCache(key = '#{key}')
        def evict(String key){
            log.debug('evict')
        }
    }


### @RedisFlexibleCache ###

This annotation takes the following parameters:

    key               - A unique key for the data cache. 
    group             - A valid key present in expireMap.
    expire            - Expire time in ms.  Will default to never so only pass a value like 3600 if you want value to expire.
    reAttachToSession - a bolean to indicate that the plugin must try to reattach any domain class cached to current sessione.

### @EvictRedisFlexibleCache ###

This annotation takes the following parameter:

    key               - A unique key for the data cache to evict. 


### Memoization Annotation Keys ###

Since the value of the key must be passed in but will also be transformed by AST, we can not use the `$` style gstring values in the keys.
Instead you will use the `#` sign to represent a gstring value such as `@EvictRedisFlexibleCache(key = "#{book.title}:#{book.id}")`.

During the AST tranformation these will be replaced with the `$` character and will evaluate correctly during runtime as `redisFlexibleCacheService.evictCache("${book.title}:${book.id}"){...}`.

This kind of evaluation is the same of ( and realized thanks to the example of): [redis-plugin-example], take a look to the link for full details.

If the compile succeeds but runtime fails or throws an exception, make sure the following are valid:
  * Your key is configured correctly.
  * The key uses a #{} for all variables you want referenced.

If the compile does NOT succeed make sure check the stack trace as some validation is done on the AST transform for each annotation type:
  * Required annotation properties are provided.
  * When using `reAttachToSession` it is a valid Bolean.
  * When using `expire` it is a valid String.
  * When using `group` it is a valid String.
  * When using `key` it is a valid String.


Release Notes
=============

* 0.1 - released 13/11/2013 - this is the first released revision of the plugin.

[redis-cache-plugin]: http://www.grails.org/plugin/cache-redis
[redis-plugin]: http://www.grails.org/plugin/redis
[redis-plugin-example]: https://github.com/grails-plugins/grails-redis#memoization-annotation-keys
[cache-plugin]: http://www.grails.org/plugin/cache
[redis]: http://redis.io
[jedis]: https://github.com/xetorthio/jedis/wiki
