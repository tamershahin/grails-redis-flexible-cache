
/*
 * Copyright (c) 2013. Gametube SAS.
 */

package org.gametube.dmg.core.cache.serializer;


/**
 * Basic interface serialization and deserialization of Objects to byte arrays (binary data).
 *
 * It is recommended that implementations are designed to handle null objects/empty arrays on serialization and deserialization side.
 * Note that Redis does not accept null keys or values but can return null replies (for non existing keys).
 *
 * @author Tamer Shahin
 */
public interface RedisSerializer<T> {

    /**
     * Serialize the given object to binary data.
     *
     * @param t object to serialize
     * @return the equivalent binary data
     */
    byte[] serialize(T t) throws SerializationException;

    /**
     * Deserialize an object from the given binary data.
     *
     * @param bytes object binary representation
     * @return the equivalent object instance
     */
    T deserialize(byte[] bytes) throws SerializationException;
}