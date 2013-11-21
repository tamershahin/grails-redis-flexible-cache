Grails Flexible Cache Redis Plugin
==================================

This plugin is an alternative to [redis-cache-plugin]. It gives the possibility to set the expire time in seconds for every cached key, and provides a service, annotations and injected methods to perform entry caching and eviction.
The [redis-plugin] plugin also provides the possibility to set a TTL for a cached entry (using the provided `@Memoize` annotation), but it lacks the option to serialize any kind of Serializable objects (only object ids are cached and then hydrated from main DB).
This means that potentially a <b>lot</b> of data will go to redis, so pay attention to memory and bandwith consumption!

This plugin is not an extension of [cache-plugin] plugin, it is far more simple and lighter at the same time.
The [cache-plugin] gives a deep integration with grails Controller CoC mechanism, but I think it creates too much overhead sometimes.

The cache implementation provided by this plugin is inspired by [redis-cache-plugin] and [redis-plugin] but is not based on them.
This plugins depends on [redis-plugin] for communication with redis and therefore it uses its configuration DSL.

Installation
------------
Dependency :

    compile ":redis-flexible-cache:0.1" 

In order to access the redis server where cached entries are stored, the plugin uses the configuration of the [redis-plugin]. 
Typically, you'd have something like this in your `grails-app/conf/Config.groovy` file:

    grails.redisflexiblecache.connectiontouse = 'cache' // not mandatory. If not declared 'cache' is the default value
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
                    host = 'localhost'  // will override the base one
                    defaultTTL = 10 * 60 // seconds (used only if no ttl are declared in the annotation/map and no expireMap is defined
                    expireMap = [ // values in seconds
                            never: -1, // negative values mean do not set any TTL
                            low: 10 * 60,
                            mid_low: 5 * 60,
                            mid: 2 * 60,
                            high: 1 * 60
                    ]
                }
            }
        }
    }

There are two additional entries in the configuration respect a standard redis plugin configuration:
 * `defaultTTL`: indicates the amount of seconds to use as TTL when no other TTL value is provided explicitly
 * `expireMap`: provides names and values (in seconds) for TTL presets that can be used in annotations/method calls

There is also a specific configuration key that allows selecting which redis connection to use:
 * `grails.redisflexiblecache.connectiontouse` : the redis connection that the plugin must use. Can be 'cache' or any other specified in the redis plugin configuration. If not found, 'cache' is the default one. If there is no 'cache' connection, the base connection will be used.

Plugin Usage
------------

## Parameters ##

As explained below, the plugin can be used in several ways (service, annotations, injected methods), but the set of parameters availble are always the same.
In case of storing/retrieving values from the cache the values are:

    key               - a String containing a unique key for the cached data.
    group             - a String containing a valid key present in the expireMap defined in Config.groovy.
    expire            - an Integer containing the expire time in seconds (takes preference from group if both are provided).  Will default to 300 (5 minutes) if no valid group or expire values are provided.  If you set 0 or a negative value, entries will never expire.
    reAttachToSession - a Boolean indicating whether the plugin must try to reattach any domain class retrieved from cache to the current hibernate session.

In case of evicting values from the cache the value is only:

    key               - a String containing a key to evict from the cache. You can insert also `*` and `?` wildcards to evict a set of keys as per Redis specification (`*`: 0 or more characters; `?`: exactly 1 character).


### redisFlexibleCacheService Bean ###

    def redisFlexibleCacheService

The `redisFlexibleCacheService` has 2 methods: 
 * `doCache`: stores/retrieves an object from the cache using a given key.
 * `evictCache` evicts a key (or several if the key contains a wildcard) and its value from the cache.

This service is a standard Spring bean that can be injected and used in controllers, services, etc.

Example:
    
    redisFlexibleCacheService.doCache('some:key:1', 'someGroup', 120, true, {
        return somethingToCache
    })

    redisFlexibleCacheService.evictCache( 'some:key:*', {
                log.debug('evicted :' + keys);
    })

### Controllers/Services dynamic methods ###

At application startup/reload each Grails controller and service gets injected the two methods provided from redisFlexibleCacheService with the following names:
 * `cache`
 * `evictCache`

Here is an example of usage:

    def index(){
      //params validation, ecc...
      def res = cache group: 'longLasting', key: 'key:1', reAttachToSession: true, {
                  def bookList = // long lasting algortitm that returns a collection of domain class instances
                  def calculated = // long lasting algoritm that returns an Integer  
                  [book: bookList, statistics: calculated]
              }
       // ..
       return [result: res]
    }

### Annotations ###

It is also possible to use two Annotations on methods to access the cache functionality: 
 * `@RedisFlexibleCache`
 * `@EvictRedisFlexibleCache`

The use of these annotations results in an AST transformation that wraps the body of the method with a call to the corresponding method in the `redisFlexibleCacheService`.

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


#### Using variables in Annotation keys ####

Since the value of the key must be passed in but will also be transformed by AST, we can not use the `$` style gstring values in the keys.
Instead you will use the `#` sign to represent a gstring value such as `@EvictRedisFlexibleCache(key = "#{book.title}:#{book.id}")`.

During the AST tranformation these will be replaced with the `$` character and will evaluate correctly during runtime as `redisFlexibleCacheService.evictCache("${book.title}:${book.id}"){...}`.

This kind of evaluation is the same used in (and implemented thanks to the example of) [redis-plugin-example]; take a look to the link for full details.

If the compile succeeds but runtime fails or throws an exception, make sure the following conditions are met:
  * Your key is configured correctly.
  * The key uses a #{} for all variables you want referenced.

If the compile does NOT succeed, check the stack trace as some validation is done on the AST transform for each annotation type. These conditions must be verified:
  * Required annotation properties are provided.
  * When using `reAttachToSession`, it is a valid Boolean.
  * When using `expire`, it is a valid <b>String<b> with a numeric value.
  * When using `group`, it is a valid String.
  * When using `key`, it is a valid String.


Release Notes
=============

* 0.1 - released 13/11/2013 - this is the first released revision of the plugin.


Credits
=======

This plugin is sponsored by <b>[GameTube]</b>.

Thanks to the authors of [redis-plugin] and [redis-cache-plugin] for excellent code examples and Christian Oestreich for his [guide on groovy AST]

[redis-cache-plugin]: http://www.grails.org/plugin/cache-redis
[redis-plugin]: http://www.grails.org/plugin/redis
[redis-plugin-example]: https://github.com/grails-plugins/grails-redis#memoization-annotation-keys
[cache-plugin]: http://www.grails.org/plugin/cache
[redis]: http://redis.io
[jedis]: https://github.com/xetorthio/jedis/wiki
[GameTube]: http://www.gametube.org/
[guide on groovy AST]: http://www.christianoestreich.com/2012/02/groovy-ast-transformations-part-1/
