package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.ugent.idlab.knows.functions.agent.Agent;
import be.ugent.rml.Executor;
import be.ugent.rml.StrictMode;
import be.ugent.rml.conformer.MappingConformer;
import be.ugent.rml.records.RecordsFactory;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.store.QuadStoreFactory;
import be.ugent.rml.term.NamedNode;

/**
 * rml-map: the {@link App} processor as a command-line tool.
 *
 * <p>
 * Input is NDJSON on stdin: each line is one JSON record, or a JSON array of
 * records (one unit of several records). The mapping reads them through a
 * logical source registered under {@code --source} (default {@code stdin}),
 * always presented as a JSON array so one mapping serves every mode: the line's
 * records per line when streaming, all records of all lines with
 * {@code --batch}. Iterate it with {@code $[*]}.
 *
 * <p>
 * Output is NDJSON on stdout: one line per execution, a JSON string holding the
 * default target serialised in {@code --format}.
 *
 * <p>
 * Warm-up happens once per process, exactly as in the processor: the FnO
 * function agent, the parsed mappings and the records factory. Every execution
 * then builds a new {@link Executor} and calls {@code execute()}.
 */
public class RmlMap {
    static final String USAGE = String.join("\n",
            "Usage: rml-map --mapping <file> [--mapping <file>...] [--base <IRI>] [--format nquads]",
            "               [--source <name>] [--batch]",
            "",
            "Maps NDJSON from stdin (a record or an array of records per line), writing one NDJSON",
            "line (a JSON string of RDF) per execution.",
            "",
            "  --mapping <file>  RML mapping (repeatable, required)",
            "  --base <IRI>      base IRI (default: " + App.DEFAULT_BASE_IRI + ")",
            "  --format <fmt>    output format: nquads, ntriples, turtle, trig, jsonld, ... (default: nquads)",
            "  --source <name>   name the mapping's logical source uses for stdin (default: stdin)",
            "  --batch           one execution over all lines instead of one per line",
            "  -h, --help        show this help");

    static class Options {
        List<String> mappings = new ArrayList<>();
        String baseIRI = App.DEFAULT_BASE_IRI;
        String format = "nquads";
        String source = "stdin";
        boolean batch = false;
    }

    public static void main(String[] argv) throws Exception {
        Options opts = parseArgs(argv);

        // Warm-up, once per process.
        Agent functionAgent = App.createFunctionAgent();

        Map<String, CacheReader> readers = new HashMap<>();
        CacheReader input = new CacheReader();
        readers.put(opts.source, input);
        RecordsFactory recordsFactory = new MyRecordsFactory(readers);

        List<QuadStore> rmlStores = new ArrayList<>();
        for (String mapping : opts.mappings) {
            try (InputStream in = new FileInputStream(mapping)) {
                QuadStore rmlStore = QuadStoreFactory.read(in);
                new MappingConformer(rmlStore, new HashMap<>()).conform();
                rmlStores.add(rmlStore);
            }
        }

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Writer stdout = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(FileDescriptor.out), StandardCharsets.UTF_8), 1 << 16);

        boolean failed = false;
        if (opts.batch) {
            StringBuilder all = new StringBuilder("[");
            String line;
            while ((line = stdin.readLine()) != null) {
                String records = arrayBody(line);
                if (records.isBlank()) continue;
                if (all.length() > 1) all.append(',');
                all.append(records);
            }
            all.append(']');

            input.set(all.toString().getBytes(StandardCharsets.UTF_8));
            failed = !executeOnce(rmlStores, recordsFactory, functionAgent, opts, stdout);
        } else {
            String line;
            while ((line = stdin.readLine()) != null) {
                if (line.isBlank()) continue;

                input.set(("[" + arrayBody(line) + "]").getBytes(StandardCharsets.UTF_8));
                failed |= !executeOnce(rmlStores, recordsFactory, functionAgent, opts, stdout);
                // Streaming: hand every unit downstream as soon as it is mapped.
                stdout.flush();
            }
        }

        stdout.flush();
        System.exit(failed ? 1 : 0);
    }

    /**
     * The records on one input line, as the inside of a JSON array: a line that
     * already is an array loses its brackets, a single record is kept as is.
     * String work only, so no JSON is parsed outside the mapper.
     */
    static String arrayBody(String line) {
        String t = line.strip();
        if (t.startsWith("[") && t.endsWith("]")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /**
     * One execution over whatever the input reader currently holds, like
     * {@code App.executeOnce()}. Writes a single NDJSON line.
     *
     * @return false if a mapping failed; the failure is logged and skipped, as
     *         the processor does.
     */
    static boolean executeOnce(List<QuadStore> rmlStores, RecordsFactory recordsFactory, Agent functionAgent,
            Options opts, Writer out) throws Exception {
        boolean ok = true;
        ByteArrayOutputStream rdf = new ByteArrayOutputStream();

        for (QuadStore rmlStore : rmlStores) {
            try {
                var engine = new Executor(rmlStore, recordsFactory, null, opts.baseIRI, StrictMode.BEST_EFFORT,
                        functionAgent);
                var maps = engine.execute(null, true, null);

                var store = maps.get(new NamedNode("rmlmapper://default.store"));
                if (store != null) {
                    store.write(rdf, opts.format);
                }
            } catch (Throwable e) {
                // See App.executeOnce(): rmlmapper reports recoverable problems as a bare Error.
                if (e instanceof VirtualMachineError || e instanceof LinkageError) {
                    throw (Error) e;
                }
                System.err.println("rml-map: error during mapping: " + e.getMessage());
                ok = false;
            }
        }

        writeJsonString(out, rdf.toString(StandardCharsets.UTF_8));
        out.write('\n');
        return ok;
    }

    static void writeJsonString(Writer out, String s) throws Exception {
        out.write('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.write("\\\"");
                case '\\' -> out.write("\\\\");
                case '\n' -> out.write("\\n");
                case '\r' -> out.write("\\r");
                case '\t' -> out.write("\\t");
                default -> {
                    if (c < 0x20) {
                        out.write(String.format("\\u%04x", (int) c));
                    } else {
                        out.write(c);
                    }
                }
            }
        }
        out.write('"');
    }

    static Options parseArgs(String[] argv) {
        Options opts = new Options();
        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--mapping" -> opts.mappings.add(value(argv, ++i));
                case "--base" -> opts.baseIRI = value(argv, ++i);
                case "--format" -> opts.format = value(argv, ++i);
                case "--source" -> opts.source = value(argv, ++i);
                case "--batch" -> opts.batch = true;
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    System.exit(0);
                }
                default -> usageError("unknown argument " + argv[i]);
            }
        }
        if (opts.mappings.isEmpty()) {
            usageError("--mapping is required");
        }
        return opts;
    }

    static String value(String[] argv, int i) {
        if (i >= argv.length) {
            usageError(argv[i - 1] + " needs a value");
        }
        return argv[i];
    }

    static void usageError(String message) {
        System.err.println("rml-map: " + message);
        System.err.println(USAGE);
        System.exit(2);
    }
}
