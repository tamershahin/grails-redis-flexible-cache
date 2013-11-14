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

package org.gametube.redisflexiblecache;

import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Class that holds the serializing and deserializing converters
 */
public class RedisFlexibleSerializer implements RedisSerializer<Object> {

    public static final byte[] EMPTY_ARRAY = new byte[0];

    protected SerializingConverter serializingConverter;
    protected DeserializingConverter deserializingConverter;

    public Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            return deserializingConverter.convert(bytes);
        } catch (Exception e) {
            throw new SerializationException("Cannot deserialize", e);
        }
    }

    public byte[] serialize(Object object) {
        if (object == null) {
            return EMPTY_ARRAY;
        }

        try {
            return serializingConverter.convert(object);
        } catch (Exception e) {
            throw new SerializationException("Cannot serialize", e);
        }
    }

    /**
     * Dependency injection for the serializingConverter.
     *
     * @param serializingConverter
     */
    public void setSerializingConverter(SerializingConverter serializingConverter) {
        this.serializingConverter = serializingConverter;
    }

    /**
     * Dependency injection for the deserializingConverter.
     *
     * @param deserializingConverter
     */
    public void setDeserializingConverter(DeserializingConverter deserializingConverter) {
        this.deserializingConverter = deserializingConverter;
    }
}
