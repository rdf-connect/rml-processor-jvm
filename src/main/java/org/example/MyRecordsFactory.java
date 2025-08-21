
package org.example;

import java.lang.reflect.Field;

import java.util.Map;

import be.ugent.rml.records.RecordsFactory;
import io.github.rdfc.IReader;

public class MyRecordsFactory extends RecordsFactory {

    public MyRecordsFactory(Map<String, CacheReader> readers) {
        super(null, null);
        var factory = new MyAccessFactory(readers);

        try {
            Field f = RecordsFactory.class.getDeclaredField("accessFactory");
            f.setAccessible(true);
            f.set(this, factory);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
        }
    }
}
