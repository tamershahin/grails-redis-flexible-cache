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
     * If @reAttachToSession is true the method will rehydrate domain classes instances using the id retrieved from the
     * cache to the in order to have them fully connected to current hibernate session.
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
            return reHydrateIntoSessionIfNecessary(deserializedResult, reAttachToSession)
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
            def serializedResult = dehydratedBeforeSerializationIfNecessary(result, reAttachToSession)
            if (serializedResult) {
                redisService.withRedis { Jedis redis ->
                    if (ttl > 0) {
                        redis.setex(key, ttl, serializedResult)
                    } else {
                        redis.set(key, serializedResult)
                    }
                }
            }
            return result
        }
    }

    /**
     * Recursively inspect results of the closure execution to cache. The original result, that may contain Collection,
     * Maps, Domain Classes or POGOs, are replaced with RedisFlexibleCacheValueWrappers instances. In case of Domain
     * Classes the RedisFlexibleCacheValueWrappers instance holds its ids that will be used to retrieve the object from
     * the DB.
     * In all other cases the RedisFlexibleCacheValueWrappers instances will hold directly the value of results objects
     * preserving their type.
     *
     * @param obj the object to navigate
     */
    private def dehydratedBeforeSerializationIfNecessary(def obj, boolean reAttachToSession) {
        def dataHolder
        if (obj instanceof Collection) {
            dataHolder = []
            dataHolder.addAll(obj.collect {
                dehydratedBeforeSerializationIfNecessary(it, reAttachToSession)
            })
        } else if (obj instanceof Map) {
            dataHolder = [:]
            obj.collectEntries { k, v ->
                dataHolder.put(k: dehydratedBeforeSerializationIfNecessary(v, reAttachToSession))
            }
        } else if (obj != null) {
            if (grailsApplication.isDomainClass(obj.class) && reAttachToSession) {
                dataHolder = new RedisFlexibleCacheValueWrapper(classType: obj.class, value: obj.id, isDomainClass: true)
            } else {
                dataHolder = new RedisFlexibleCacheValueWrapper(classType: obj.class, value: obj, isDomainClass: false)
            }
        }
        return dataHolder
    }

    /**
     * Recursively inspect results deserialized from cache and try to load domain classes instances from the current
     * hibernate session using their deserialized ids. Can handle Collections and Maps in search for domain classes.
     *
     * @param obj the object to reattach (deserialized from cache)
     * @return the same object but with domain classes (if any) reattached to session
     */
    private def reHydrateIntoSessionIfNecessary(def obj, boolean reAttachToSession) {
        def dataHolder
        if (obj instanceof Collection) {
            dataHolder = []
            dataHolder.addAll(obj.collect {
                reAttachToSessionIfDomainClass(it, reAttachToSession)
            })
        } else if (obj instanceof Map) {
            dataHolder = [:]
            obj.collectEntries { k, v ->
                dataHolder.put(k: reAttachToSessionIfDomainClass(v, reAttachToSession))
            }
        } else if (obj != null) {
            dataHolder = reAttachToSessionIfDomainClass(obj, reAttachToSession)
        }
        return dataHolder
    }

    /**
     * If @obj is a wrapper containing domain classes then it try to load it from session/db.
     * If @obj is not a wrapper for a domain class unwrap directly it returning obj.value.
     *
     * @param obj the object to reattach if it is a domain class.
     */
    private def reAttachToSessionIfDomainClass(RedisFlexibleCacheValueWrapper obj, boolean reAttachToSession) {
        try {
            if (obj != null) {
                if (obj.isDomainClass && reAttachToSession) {
                    def fresh = obj.classType.get(obj.value)
                    return fresh
                } else {
                    return obj.value
                }
            }
            return obj
        }
        catch (Exception e) {
            log.error("error attaching object to session. is it a domain class?", e)
        }

    }

}
