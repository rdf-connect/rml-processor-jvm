
package org.example;

import java.util.concurrent.CompletableFuture;

import io.github.rdfc.IReader;

public class CacheReader {
    protected byte[] _last;

    public final CompletableFuture<Void> isDone;

    public CacheReader(IReader reader) {
        this.isDone = reader.buffers().on(buffer -> {
            _last = buffer.toByteArray();
        });
    }

    byte[] last() {
        return this._last;
    }
}
