/*
 * Copyright (c) 2013. Gametube SAS.
 */

package org.gametube.dmg.core.cache.serializer;




import org.springframework.core.NestedRuntimeException;

/**
 * Generic exception indicating a serialization/deserialization error.
 *
 * @author Tamer Shahin
 */
public class SerializationException extends NestedRuntimeException {

    /**
     * Constructs a new <code>SerializationException</code> instance.
     *
     * @param msg
     * @param cause
     */
    public SerializationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs a new <code>SerializationException</code> instance.
     *
     * @param msg
     */
    public SerializationException(String msg) {
        super(msg);
    }
}