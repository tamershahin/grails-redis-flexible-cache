package org.gametube.dmg.core.cache.serializer;

import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Class that holds the serializing and deserializing converters
 * @author Tamer Shahin
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
