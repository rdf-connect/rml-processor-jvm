
package org.example;

import java.util.List;
import java.util.Map;

import be.ugent.idlab.knows.dataio.access.Access;
import be.ugent.rml.NAMESPACES;
import be.ugent.rml.Utils;
import be.ugent.rml.access.AccessFactory;
import be.ugent.rml.store.QuadStore;
import be.ugent.rml.term.Literal;
import be.ugent.rml.term.NamedNode;
import be.ugent.rml.term.Term;

public class MyAccessFactory extends AccessFactory {
    protected Map<String, CacheReader> readers;

    public MyAccessFactory(Map<String, CacheReader> readers) {
        super(null, null);
        this.readers = readers;
    }

    public Access getAccess(Term logicalSource, QuadStore rmlStore) {

        List<Term> sources = Utils
                .getObjectsFromQuads(rmlStore.getQuads(logicalSource, new NamedNode(NAMESPACES.RML2 + "source"), null));

        // check if at least one source is available.
        if (!sources.isEmpty()) {
            Term source = sources.get(0);

            // if we are dealing with a literal,
            // then it's either a local or remote file.
            if (!(sources.get(0) instanceof Literal)) {
                // if not a literal, then we are dealing with a more complex description.
                List<Term> sourceType = Utils
                        .getObjectsFromQuads(rmlStore.getQuads(source, new NamedNode(NAMESPACES.RDF + "type"), null));

                sourceType.remove(new NamedNode(NAMESPACES.RML2 + "Source"));

                switch (sourceType.get(0).getValue()) {
                    case Ns.RDFC + "Source":
                        String readerId = Utils
                                .getObjectsFromQuads(rmlStore.getQuads(source, new NamedNode(Ns.RDFC + "reader"), null))
                                .get(0).getValue();

                        String type = "";
                        try {

                            type = Utils
                                    .getObjectsFromQuads(
                                            rmlStore.getQuads(source, new NamedNode(Ns.RDFC + "type"), null))
                                    .get(0).getValue();
                        } catch (Exception e) {

                        }

                        var reader = this.readers.get(readerId);
                        return new ReaderAccess(source.getValue(), type, reader);
                }
            }
        }

        return super.getAccess(logicalSource, rmlStore);
    }
}
