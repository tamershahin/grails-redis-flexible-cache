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

package org.gametube.redisflexiblecache

import org.gametube.redisflexiblecache.RedisSerializer
import redis.clients.jedis.Jedis

import java.util.regex.Pattern

class RedisFlexibleCachingService {

    def redisService

    def grailsApplication
    RedisSerializer redisFlexibleSerializer

    Map expireMap
    def defaultTTL
    Boolean enabled = true

    static final Pattern WILDCARD_PATTERN = ~/.*[\*\?]+.*/

    /**
     * Evict the cache for the @keyString parameters and then execute the @closure call (use mainly for logging)
     * If the @keyString contains '*' or '?' the method assumes that it must load all keys from redis that match the given expression.
     *
     * @param keyString can be and exact key or a wildcard key like 'book:*:author:alighieri'
     * @param closure to call after evicting (use mainly for logging)
     * @return
     */
    def evictCache(String keyString, Closure closure = null) {

        if (!enabled) {
            return closure ? closure() : false
        }

        Set keys = []
        if (keyString.matches(WILDCARD_PATTERN)) {
            redisService.withRedis { Jedis redis ->
                keys = redis.keys("${keyString}".bytes)
            }
            if (log.isDebugEnabled()) {
                log.debug("found ${keys.size()} keys for wildchar string= ${keyString}")
            }
        } else {
            keys.add(keyString.bytes)
        }

        redisService.withRedis { Jedis redis ->
            keys.each { byte[] key ->
                redis.expire(key, 0)
                if (log.isDebugEnabled()) {
                    log.debug("evicted the key : ${new String(key)}")
                }
            }
        }

        if (closure)
            closure()
    }

    /**
     * If the keys is already in cache the methods returns it. Otherwise it performs the caching of the result of the @closure call.
     * If @group and @ttl are both valid @ttl is the only one considered. If none are valid try to use the defaultTTL value configured
     * into Config.groovy (see documentation to set it properly).
     * If @reAttachToSession is true the method try to inspect the object stored in cache searching for domain classes.
     *
     * @param keyString the key for the current value to store
     * @param group the key of the map set in config.groovy with standard expire times
     * @param ttl time to live for current key in seconds
     * @param reAttachToSession trigger the attempt to reattach domain classes to the sessione
     * @param closure the result of the closures call is what will be stored in cache
     * @return the result of closure call or what is retrieved from cache (if the key exists in the cache)
     */
    def doCache(String keyString, String group, Integer ttl, boolean reAttachToSession = false, Closure closure) {

        if (!enabled) {
            return closure()
        }

        def key = keyString.bytes

        if (log.isDebugEnabled()) {
            log.debug "using key $key"
        }
        def result = redisService.withRedis { Jedis redis ->
            redis.get(key)
        }

        if (!result) {
            //ttl has the priority. if not set try to use the group
            if (!ttl || ttl == -1) {
                //if the group is defined use it
                if (group && !expireMap.isEmpty()) {
                    ttl = expireMap[group]
                }
                if (!ttl || ttl == -1) {
                    ttl = defaultTTL
                }
                if (!ttl || ttl == -1) {
                    ttl = 60 * 5
                }
            }
            if (log.isDebugEnabled()) {
                log.debug "cache miss: $key"
            }
            def value = closure()
            result = redisFlexibleSerializer.serialize(value)
            if (result) redisService.withRedis { Jedis redis ->
                if (ttl) {
                    redis.setex(key, ttl, result)
                } else {
                    redis.set(key, result)
                }
            }
            return value
        } else {
            if (log.isDebugEnabled()) {
                log.debug "cache hit : $key = $result"
            }
        }

        def fromCache = redisFlexibleSerializer.deserialize(result)

        if (reAttachToSession) {
            navigateFromCache(fromCache)
        }

        return fromCache
    }

    /**
     * Recursively inspect what has been deserialized from cache. Can handle Collections and Maps in search of domain classes.
     * When it founds one it tries to reattach to the session.
     * @param fromCache the object deserialized from cache
     * @return the same object but with domain classes reattached to session (if there are any)
     */
    def navigateFromCache(def fromCache) {
        if (fromCache instanceof Collection) {
            fromCache.each {
                navigateFromCache(it)
            }
        } else if (fromCache instanceof Map) {
            fromCache.each { k, v ->
                navigateFromCache(v)
            }
        } else {
            reAttachToSessionIfPossible(fromCache)
        }
    }

    /**
     * If @obj is a domain class try to load it from session if possible or force the reload from db.
     * Do nothig if not a domain class
     * @param obj the object to reattach if it is a domain class.
     */
    def reAttachToSessionIfPossible(def obj) {
        try {
            if (obj.class in grailsApplication.domainClasses*.clazz && !obj.isAttached()) {
                def fresh = obj.load(obj.id)
                if (!fresh) {
                    fresh = obj.refresh()
                    if (log.isDebugEnabled()) {
                        log.debug(obj.class.getName() + ' refreshed')
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(obj.class.getName() + ' loaded')
                    }
                }
                obj = fresh
            }
        }
        catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("error attaching object to session. is it a domain class? ${e.getMessage()}")
            }
        }

    }

}
