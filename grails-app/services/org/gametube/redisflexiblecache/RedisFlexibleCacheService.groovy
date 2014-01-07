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

import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline

import java.util.regex.Pattern

/**
 * Service providing the basic cache and evict functionality
 */
class RedisFlexibleCacheService {

    static transactional = false

    def redisService

    def grailsApplication
    RedisSerializer redisFlexibleSerializer

    Map expireMap
    def defaultTTL
    Boolean enabled = true

    static final Pattern WILDCARD_PATTERN = ~/.*[\*\?]+.*/

    /**
     * Evict the cache for the @keyString entry and then execute the @closure call (use mainly for logging)
     * If @keyString contains '*' or '?' the method assumes that it must load all keys from redis that match the given
     * expression.
     *
     * @param keyString can be and exact key or a wildcard key like 'book:*:author:alighieri'
     * @param closure to call after evicting (use mainly for logging)
     * @return
     */
    def evictCache(String keyString, Closure closure = null) {
        // if not enabled, just return the result of the closure
        if (!enabled) {
            return closure ? closure() : null
        }

        // get the keys to evict depending on whether the key is a wildcard or not
        Set keys = []
        if (keyString.matches(WILDCARD_PATTERN)) {
            redisService.withRedis { Jedis redis ->
                keys = redis.keys(keyString.bytes)
            }
            if (log.isDebugEnabled()) {
                log.debug("found ${keys.size()} keys for wildcard string '${keyString}'")
            }
        } else {
            keys.add(keyString.bytes)
        }

        // expire all the keys
        redisService.withPipeline { Pipeline pipeline ->
            keys.each { byte[] key ->
                pipeline.expire(key, 0) // setting expire time = 0 will work for keys already with ttl and the one without ttl
                if (log.isDebugEnabled()) {
                    log.debug("evicted the key : ${new String(key)}")
                }
            }
        }

        // return the result of  closure
        return closure ? closure() : null
    }

    /**
     * If the there is already an entry associated to @keyString, it is returned. Otherwise, the provided @closure is
     * called and its result is stored in the cached associated to the given key.
     * The TTL value to be used is controlled by the @group (for using a preset) and @ttl (amount of seconds)
     * properties. If both provided and valid, only @ttl is taken into account. If none are provided nor valid,
     * the defaultTTL value configured in Config.groovy will be used (see documentation to set it properly).
     * If @reAttachToSession is true the method will reattach domain classes instances retrieved from the cache to the
     * current hibernate session.
     *
     * @param keyString the key used to cache/retrieve the current value
     * @param group a key of the expireMap set in Config.groovy with preset expire times
     * @param ttl time to live for current key in seconds (takes precedence from  @group parameter)
     * @param reAttachToSession reattach domain classes to the hibernate session when retrieving results from cache
     * @param closure the Closure that will provide the value to store in the cache
     * @return the result of closure call or what the value retrieved from cache if the key already exists in the cache
     */
    def doCache(String keyString, String group, Integer ttl, boolean reAttachToSession = false, Closure closure) {
        // if not enabled, just return the result of the closure
        if (!enabled) {
            return closure()
        }

        def key = keyString.bytes

        if (log.isDebugEnabled()) {
            log.debug "using key $key"
        }

        // try to get the result directly from the cache
        def result = redisService.withRedis { Jedis redis ->
            redis.get(key)
        }

        if (result) { // if the result was in the cache, deserialize it
            if (log.isDebugEnabled()) {
                log.debug "cache hit : $key = $result"
            }
            def deserializedResult = redisFlexibleSerializer.deserialize(result)

            // if needed, reatach retrieved domain class instances to the session
            if (reAttachToSession) {
                recursiveReAttachToSession(deserializedResult)
            }

            return deserializedResult
        } else { // if the result was not in the cache, cache it

            // get the TTL to use: ttl has the priority; if not set, try to use the group
            if (!ttl) {
                //if the group is defined use it
                if (group && !expireMap.isEmpty()) {
                    ttl = expireMap[group]
                }
                ttl = ttl ?: defaultTTL
                ttl = ttl ?: 60 * 5 // default to 5 minutes
            }
            if (log.isDebugEnabled()) {
                log.debug "cache miss: $key"
            }
            result = closure()
            recursiveNavigateBeforeSerialize(result)
            def serializedResult = redisFlexibleSerializer.serialize(result)
            if (serializedResult) redisService.withRedis { Jedis redis ->
                if (ttl > 0) {
                    redis.setex(key, ttl, serializedResult)
                } else {
                    redis.set(key, serializedResult)
                }
            }
            return result
        }
    }

    /**
     * Recursively inspect results of the closure execution to cache. This allow all persistent properties in domain
     * classes to cache to have a proper value (and not only the lazy loader handler) before serialization.
     * @param obj the object to navigate
     */
    private void recursiveNavigateBeforeSerialize(obj) {
        if (obj instanceof Collection) {
            obj.each {
                recursiveNavigateBeforeSerialize(it)
            }
        } else if (obj instanceof Map) {
            obj.each { k, v ->
                recursiveNavigateBeforeSerialize(v)
            }
        } else {
            obj.properties.each { k, v ->
                if (v && grailsApplication.isDomainClass(v.class)) {
                    recursiveNavigateBeforeSerialize(v)
                }
            }
        }
    }

    /**
     * Recursively inspect results deserialized from cache and try to reattach domain class instances to the current
     * hibernate session. Can handle Collections and Maps in search for domain classes.
     * @param obj the object to reattach (deserialized from cache)
     * @return the same object but with domain classes (if any) reattached to session
     */
    private recursiveReAttachToSession(obj) {
        if (obj instanceof Collection) {
            obj.each {
                recursiveReAttachToSession(it)
            }
        } else if (obj instanceof Map) {
            obj.each { k, v ->
                recursiveReAttachToSession(v)
            }
        } else {
            reAttachToSessionIfPossible(obj)
        }
    }

    /**
     * If @obj is a domain class try to load it from session if possible or force the reload from db.
     * Do nothig if not a domain class
     * @param obj the object to reattach if it is a domain class.
     */
    private reAttachToSessionIfPossible(obj) {
        try {
            if (grailsApplication.isDomainClass(obj.class) && !obj.isAttached()) {
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
