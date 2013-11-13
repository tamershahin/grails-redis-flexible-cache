package org.gametube.dmg.core.cache.serializer;

import org.springframework.core.NestedIOException;
import org.springframework.core.serializer.Deserializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * Custom Deserializer (faster then {@link org.springframework.core.serializer.DefaultDeserializer} )
 * @author Tamer Shahin
 */
public class RedisFlexibleDeserializer implements Deserializer<Object> {

    public Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(inputStream) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass osc) throws IOException, ClassNotFoundException {
                try {
                    return Thread.currentThread().getContextClassLoader().loadClass(osc.getName());
                }
                catch (Exception e) {
                    return super.resolveClass(osc);
                }
            }
        };

        try {
            return ois.readObject();
        }
        catch (ClassNotFoundException e) {
            throw new NestedIOException("Failed to deserialize object type", e);
        }
    }
}
