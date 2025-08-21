
package org.example;

import io.github.rdfc.IReader;

public class CacheReader {
    protected byte[] _last;

    public CacheReader(IReader reader) {
        reader.buffers().on(buffer -> {
            if (buffer.isPresent()) {
                _last = buffer.get().toByteArray();
            }
        });
    }

    byte[] last() {
        return this._last;
    }
}
