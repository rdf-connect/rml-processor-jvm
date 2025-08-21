
package org.example;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;

import be.ugent.idlab.knows.dataio.access.Access;

public class ReaderAccess implements Access {
    protected CacheReader reader;
    protected String id;
    protected String type;

    public ReaderAccess(String id, String type, CacheReader reader) {
        this.id = id;
        this.reader = reader;
        this.type = type;
        System.out.println("Creating reader access");
    }

    @Override
    public InputStream getInputStream() throws Exception {
        System.out.println("Get Input Stream " + this.id);
        return new ByteArrayInputStream(this.reader.last());
    }

    @Override
    public Map<String, String> getDataTypes() {
        // return Collections.singletonMap(getAccessPath(), this.type);
        return Map.of();
    }

    @Override
    public String getContentType() {
        return this.type;
    }

    @Override
    public String getAccessPath() {
        return this.id;
    }

}
