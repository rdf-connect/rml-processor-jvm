
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

        for (Term source : sources) {
            if (source instanceof Literal) {
                var reader = this.readers.get(source.getValue());
                if (reader != null) {
                    // A bare literal source carries no mime type, so we cannot derive one.
                    // Leave it empty, like the rdfc:Source branch does when rdfc:mimeType is absent.
                    return new ReaderAccess(source.getValue(), "", reader);
                }
            } else {
                List<Term> sourceType = Utils
                        .getObjectsFromQuads(rmlStore.getQuads(source, new NamedNode(NAMESPACES.RDF + "type"), null));

                sourceType.remove(new NamedNode(NAMESPACES.RML2 + "Source"));

                if (sourceType.isEmpty()) continue;

                switch (sourceType.get(0).getValue()) {
                    case Ns.RDFC + "Source": {
                        String readerId = Utils
                                .getObjectsFromQuads(rmlStore.getQuads(source, new NamedNode(Ns.RDFC + "reader"), null))
                                .get(0).getValue();

                        String type = firstValue(rmlStore, source, Ns.RDFC + "mimeType", "");

                        var reader = this.readers.get(readerId);
                        return new ReaderAccess(source.getValue(), type, reader);
                    }
                    case Ns.DCAT + "Distribution": {
                        // MappingConformer converts rml:source "id" into a dcat:Distribution
                        // with dcat:downloadURL "id". Look up the URL in our readers map.
                        String readerId = firstValue(rmlStore, source, Ns.DCAT + "downloadURL", null);
                        if (readerId != null) {
                            var reader = this.readers.get(readerId);
                            if (reader != null) {
                                String type = firstValue(rmlStore, source, Ns.DCAT + "mediaType", "");
                                return new ReaderAccess(readerId, type, reader);
                            }
                        }
                        break;
                    }
                }
            }
        }

        return super.getAccess(logicalSource, rmlStore);
    }

    /** Value of the first {@code predicate} object on {@code subject}, or {@code fallback} if there is none. */
    private static String firstValue(QuadStore rmlStore, Term subject, String predicate, String fallback) {
        List<Term> values = Utils.getObjectsFromQuads(rmlStore.getQuads(subject, new NamedNode(predicate), null));
        return values.isEmpty() ? fallback : values.get(0).getValue();
    }
}
