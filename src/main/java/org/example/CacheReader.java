
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

    /** A reader that is fed by hand through {@link #set}, as the {@link RmlMap} CLI does from stdin. */
    CacheReader() {
        this.isDone = CompletableFuture.completedFuture(null);
    }

    void set(byte[] last) {
        this._last = last;
    }

    byte[] last() {
        return this._last;
    }
}
