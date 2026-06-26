
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
                    return new ReaderAccess(source.getValue(), "application/json", reader);
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

                        String type = "";
                        try {
                            type = Utils
                                    .getObjectsFromQuads(
                                            rmlStore.getQuads(source, new NamedNode(Ns.RDFC + "mimeType"), null))
                                    .get(0).getValue();
                        } catch (Exception e) {
                        }

                        var reader = this.readers.get(readerId);
                        return new ReaderAccess(source.getValue(), type, reader);
                    }
                    case "http://www.w3.org/ns/dcat#Distribution": {
                        // MappingConformer converts rml:source "id" into a dcat:Distribution
                        // with dcat:downloadURL "id". Look up the URL in our readers map.
                        List<Term> urls = Utils.getObjectsFromQuads(
                                rmlStore.getQuads(source, new NamedNode("http://www.w3.org/ns/dcat#downloadURL"), null));
                        if (!urls.isEmpty()) {
                            String readerId = urls.get(0).getValue();
                            var reader = this.readers.get(readerId);
                            if (reader != null) {
                                return new ReaderAccess(readerId, "application/json", reader);
                            }
                        }
                        break;
                    }
                }
            }
        }

        return super.getAccess(logicalSource, rmlStore);
    }
}
