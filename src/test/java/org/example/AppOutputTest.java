package org.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import be.ugent.rml.store.QuadStore;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;
import com.google.protobuf.ByteString;
import io.github.rdfc.IWriter;
import io.github.rdfc.Stream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppOutputTest {

    private static final class CapturingWriter extends IWriter {
        private final List<ByteString> directChunks = new ArrayList<>();
        private final List<ByteString> streamedChunks = new ArrayList<>();
        private final ByteArrayOutputStream streamedBytes = new ByteArrayOutputStream();
        private boolean streamOpened = false;
        private boolean streamClosed = false;

        @Override
        public String id() {
            return "capturing-writer";
        }

        @Override
        public CompletableFuture<Stream<ByteString>> stream() {
            this.streamOpened = true;
            return CompletableFuture.completedFuture(new Stream<>() {
                @Override
                public CompletableFuture<Void> chunk(ByteString chunk) {
                    streamedChunks.add(chunk);
                    try {
                        streamedBytes.write(chunk.toByteArray());
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletableFuture<Void> close() {
                    streamClosed = true;
                    return CompletableFuture.completedFuture(null);
                }
            });
        }

        @Override
        public CompletableFuture<Void> chunk(ByteString chunk) {
            this.directChunks.add(chunk);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void outputDefaultTarget_streamMode_emitsPayloadAndClosesStream() throws Exception {
        App.Args args = new App.Args();
        args.baseIRI = App.DEFAULT_BASE_IRI;
        args.sources = List.of();
        args.targets = List.of();

        App.Target defaultTarget = new App.Target();
        CapturingWriter writer = new CapturingWriter();
        defaultTarget.writer = writer;
        defaultTarget.format = "trig";
        args.defaultTarget = defaultTarget;

        App app = new App(args, Logger.getAnonymousLogger());

        byte[] payload = new byte[App.DEFAULT_TARGET_STREAM_THRESHOLD_BYTES + 1024];
        Arrays.fill(payload, (byte) 'x');

        QuadStore store = Mockito.mock(QuadStore.class);
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write(payload);
            return null;
        }).when(store).write(any(OutputStream.class), eq("trig"));

        Map<Term, QuadStore> maps = new HashMap<>();
        maps.put(new NamedNode("rmlmapper://default.store"), store);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        Method method = App.class.getDeclaredMethod("outputDefaultTarget", Map.class, ArrayList.class);
        method.setAccessible(true);
        method.invoke(app, maps, futures);

        assertEquals(1, futures.size());
        futures.get(0).join();

        int expectedChunks = (int) Math.ceil((double) payload.length / App.DEFAULT_TARGET_CHUNK_SIZE_BYTES);

        assertTrue(writer.streamOpened, "expected stream() to be used");
        assertTrue(writer.streamClosed, "expected stream to be closed");
        assertEquals(0, writer.directChunks.size(), "expected direct chunk path to be unused");
        assertEquals(expectedChunks, writer.streamedChunks.size(), "expected streamed chunk count to match chunk size");

        byte[] emitted = writer.streamedBytes.toByteArray();
        assertTrue(emitted.length > 0, "expected streamed payload to be non-empty");
        assertArrayEquals(payload, emitted);
    }

    @Test
    void outputDefaultTarget_chunkMode_emitsPayloadWithoutStream() throws Exception {
        App.Args args = new App.Args();
        args.baseIRI = App.DEFAULT_BASE_IRI;
        args.sources = List.of();
        args.targets = List.of();

        App.Target defaultTarget = new App.Target();
        CapturingWriter writer = new CapturingWriter();
        defaultTarget.writer = writer;
        defaultTarget.format = "trig";
        args.defaultTarget = defaultTarget;

        App app = new App(args, Logger.getAnonymousLogger());

        byte[] payload = new byte[App.DEFAULT_TARGET_STREAM_THRESHOLD_BYTES - 1024];
        Arrays.fill(payload, (byte) 'y');

        QuadStore store = Mockito.mock(QuadStore.class);
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write(payload);
            return null;
        }).when(store).write(any(OutputStream.class), eq("trig"));

        Map<Term, QuadStore> maps = new HashMap<>();
        maps.put(new NamedNode("rmlmapper://default.store"), store);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        Method method = App.class.getDeclaredMethod("outputDefaultTarget", Map.class, ArrayList.class);
        method.setAccessible(true);
        method.invoke(app, maps, futures);

        assertEquals(1, futures.size());
        futures.get(0).join();

        assertTrue(!writer.streamOpened, "expected stream() not to be used");
        assertTrue(!writer.streamClosed, "expected stream close not to be used");
        assertEquals(1, writer.directChunks.size(), "expected exactly one direct chunk");
        assertEquals(0, writer.streamedChunks.size(), "expected streamed chunks to be unused");

        byte[] emitted = writer.directChunks.get(0).toByteArray();
        assertTrue(emitted.length > 0, "expected chunk payload to be non-empty");
        assertArrayEquals(payload, emitted);
    }

    @Test
    void outputToTarget_streamMode_emitsPayloadAndClosesStream() throws Exception {
        App.Args args = new App.Args();
        args.baseIRI = App.DEFAULT_BASE_IRI;
        args.sources = List.of();
        args.targets = List.of();

        App app = new App(args, Logger.getAnonymousLogger());

        App.Target target = new App.Target();
        CapturingWriter writer = new CapturingWriter();
        target.writer = writer;
        target.mappingId = "http://example.com/target-1";
        target.format = "trig";

        byte[] payload = new byte[App.DEFAULT_TARGET_STREAM_THRESHOLD_BYTES + 1024];
        Arrays.fill(payload, (byte) 'a');

        QuadStore store = Mockito.mock(QuadStore.class);
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write(payload);
            return null;
        }).when(store).write(any(OutputStream.class), eq("trig"));

        Map<Term, QuadStore> maps = new HashMap<>();
        maps.put(new NamedNode("http://example.com/target-1"), store);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        Method method = App.class.getDeclaredMethod("outputToTarget", Map.class, App.Target.class, ArrayList.class);
        method.setAccessible(true);
        method.invoke(app, maps, target, futures);

        assertEquals(1, futures.size());
        futures.get(0).join();

        int expectedChunks = (int) Math.ceil((double) payload.length / App.DEFAULT_TARGET_CHUNK_SIZE_BYTES);

        assertTrue(writer.streamOpened, "expected stream() to be used");
        assertTrue(writer.streamClosed, "expected stream to be closed");
        assertEquals(0, writer.directChunks.size(), "expected direct chunk path to be unused");
        assertEquals(expectedChunks, writer.streamedChunks.size(), "expected streamed chunk count to match chunk size");

        byte[] emitted = writer.streamedBytes.toByteArray();
        assertTrue(emitted.length > 0, "expected streamed payload to be non-empty");
        assertArrayEquals(payload, emitted);
    }

    @Test
    void outputToTarget_chunkMode_emitsPayloadWithoutStream() throws Exception {
        App.Args args = new App.Args();
        args.baseIRI = App.DEFAULT_BASE_IRI;
        args.sources = List.of();
        args.targets = List.of();

        App app = new App(args, Logger.getAnonymousLogger());

        App.Target target = new App.Target();
        CapturingWriter writer = new CapturingWriter();
        target.writer = writer;
        target.mappingId = "http://example.com/target-2";
        target.format = "trig";

        byte[] payload = new byte[App.DEFAULT_TARGET_STREAM_THRESHOLD_BYTES - 1024];
        Arrays.fill(payload, (byte) 'b');

        QuadStore store = Mockito.mock(QuadStore.class);
        Mockito.doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write(payload);
            return null;
        }).when(store).write(any(OutputStream.class), eq("trig"));

        Map<Term, QuadStore> maps = new HashMap<>();
        maps.put(new NamedNode("http://example.com/target-2"), store);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        Method method = App.class.getDeclaredMethod("outputToTarget", Map.class, App.Target.class, ArrayList.class);
        method.setAccessible(true);
        method.invoke(app, maps, target, futures);

        assertEquals(1, futures.size());
        futures.get(0).join();

        assertTrue(!writer.streamOpened, "expected stream() not to be used");
        assertTrue(!writer.streamClosed, "expected stream close not to be used");
        assertEquals(1, writer.directChunks.size(), "expected exactly one direct chunk");
        assertEquals(0, writer.streamedChunks.size(), "expected streamed chunks to be unused");

        byte[] emitted = writer.directChunks.get(0).toByteArray();
        assertTrue(emitted.length > 0, "expected chunk payload to be non-empty");
        assertArrayEquals(payload, emitted);
    }
}
