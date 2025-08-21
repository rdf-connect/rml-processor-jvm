
package org.example;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.rdfc.IReader;

public class TestReader implements IReader {

    private List<StreamIter<Iter<ByteString>>> streams = new ArrayList<>();
    private StreamIter<ByteString> string = new StreamIter<>();
    protected String id;

    public TestReader(String id) {
        this.id = id;
    }

    void msg(ByteString buffer) {
        this.string.push(buffer);
        this.streams.forEach(stream -> stream.push(
                new TestReader.SingleIter<>(buffer)));
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public Iter<Iter<ByteString>> streams() {
        var out = new StreamIter<Iter<ByteString>>();
        this.streams.add(out);
        return out;
    }

    @Override
    public StreamIter<ByteString> buffers() {
        return this.string;
    }

    private static class SingleIter<T> extends Iter<T> {
        private T item;

        SingleIter(T item) {
            this.item = item;
        }

        @Override
        public void on(Consumer<Optional<T>> cb) {
            cb.accept(Optional.of(this.item));
            cb.accept(Optional.empty());
        }
    }

    static class StreamIter<T> extends Iter<T> {
        T last = null;

        @Override
        public void on(Consumer<Optional<T>> cb) {
            super.on(cb);
            if (last != null) {
                System.out.println("StreamIter on, pushing last");
                cb.accept(Optional.of(last));
            } else {
                System.out.println("StreamIter on, not pushing last");
            }
        }

        void push(T item) {
            System.out.println("StreamIter push, setting last");
            last = item;
            this.callbacks.forEach(cb -> cb.accept(Optional.of(item)));
        }

        void end() {
            this.callbacks.forEach(cb -> cb.accept(Optional.empty()));
        }
    }
}
