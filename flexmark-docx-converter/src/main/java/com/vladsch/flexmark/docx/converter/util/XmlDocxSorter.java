package com.vladsch.flexmark.docx.converter.util;

import com.vladsch.flexmark.util.Consumer;
import com.vladsch.flexmark.util.Function;
import com.vladsch.flexmark.util.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pretty-prints docx xml, supplied as a string.
 * putting  parts in the following order:
 * <p>
 * pkg:contentType="application/vnd.openxmlformats-package.relationships+xml" pkg:name="/_rels/.rels"
 * pkg:contentType="application/vnd.openxmlformats-package.relationships+xml" pkg:name="/word/_rels/document.xml.rels"
 * pkg:contentType="application/vnd.openxmlformats-package.core-properties+xml" pkg:name="/docProps/core.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.extended-properties+xml" pkg:name="/docProps/app.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml" pkg:name="/word/header%d.xml" where %d is 1,2,3...
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml" pkg:name="/word/footer%d.xml" where %d is 1,2,3...
 * pkg:contentType="application/vnd.openxmlformats-package.relationships+xml" pkg:name="/word/_rels/footnotes.xml.rels"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml" pkg:name="/word/footnotes.xml"
 * pkg:contentType="application/vnd.openxmlformats-package.relationships+xml" pkg:name="/word/_rels/endnotes.xml.rels"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml" pkg:name="/word/endnotes.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml" pkg:name="/word/document.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml" pkg:name="/word/settings.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.webSettings+xml" pkg:name="/word/webSettings.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml" pkg:name="/word/styles.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml" pkg:name="/word/numbering.xml"
 * pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml" pkg:name="/word/fontTable.xml"
 * pkg:contentType="application/vnd.openxmlformats-package.relationships+xml" pkg:name="/customXml/_rels/item%d.xml.rels" where %d is 1,2,3...
 * pkg:contentType="application/xml" pkg:name="/customXml/item%d.xml" where %d is 1,2,3...
 * pkg:contentType="application/vnd.openxmlformats-officedocument.customXmlProperties+xml" pkg:name="/customXml/itemProps%d.xml" where %d is 1,2,3...
 * pkg:contentType="application/vnd.openxmlformats-officedocument.theme+xml" pkg:name="/word/theme/theme%d.xml" where %d is 1,2,3...
 * pkg:contentType="application/vnd.ms-word.stylesWithEffects+xml" pkg:name="/word/stylesWithEffects.xml"
 * <p>
 * <p>
 * eg.
 * {@code
 * String formattedXml = mlDocxSorter.sortDocumentParts(docxText);
 * }
 * <p>
 * StackOverflow: https://stackoverflow.com/questions/139076/how-to-pretty-print-xml-from-java
 */
public class XmlDocxSorter {

    static class DocxPartEntry {
        public final int ordinal;
        public final String contentType;
        public final String name;
        public final Pattern regex;
        public Node node;
        public final int index;

        public DocxPartEntry(final int ordinal, final String contentType, final String name) {
            this.ordinal = ordinal;
            this.contentType = contentType;
            this.name = name;

            regex = Pattern.compile("\\Q" + name.replace("%d", "\\E(\\d+)\\Q") + "\\E");
            node = null;
            index = 0;
        }

        public DocxPartEntry(DocxPartEntry other, Node node, int index) {
            ordinal = other.ordinal;
            contentType = other.contentType;
            name = other.name;
            regex = other.regex;
            this.node = node;
            this.index = index;
        }
    }

    static DocxPartEntry[] entries = new DocxPartEntry[] {
            new DocxPartEntry(0, "application/vnd.openxmlformats-package.relationships+xml", "/_rels/.rels"),
            new DocxPartEntry(1, "application/vnd.openxmlformats-package.relationships+xml", "/word/_rels/document.xml.rels"),
            new DocxPartEntry(2, "application/vnd.openxmlformats-package.core-properties+xml", "/docProps/core.xml"),
            new DocxPartEntry(3, "application/vnd.openxmlformats-officedocument.extended-properties+xml", "/docProps/app.xml"),
            new DocxPartEntry(4, "application/vnd.openxmlformats-package.relationships+xml", "/word/_rels/header%d.xml.rels"),
            new DocxPartEntry(5, "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml", "/word/header%d.xml"),
            new DocxPartEntry(6, "application/vnd.openxmlformats-package.relationships+xml", "/word/_rels/footer%d.xml.rels"),
            new DocxPartEntry(7, "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml", "/word/footer%d.xml"),
            new DocxPartEntry(8, "application/vnd.openxmlformats-package.relationships+xml", "/word/_rels/footnotes.xml.rels"),
            new DocxPartEntry(9, "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml", "/word/footnotes.xml"),
            new DocxPartEntry(10, "application/vnd.openxmlformats-package.relationships+xml", "/word/_rels/endnotes.xml.rels"),
            new DocxPartEntry(11, "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml", "/word/endnotes.xml"),
            new DocxPartEntry(12, "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", "/word/document.xml"),
            new DocxPartEntry(13, "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml", "/word/settings.xml"),
            new DocxPartEntry(14, "application/vnd.openxmlformats-officedocument.wordprocessingml.webSettings+xml", "/word/webSettings.xml"),
            new DocxPartEntry(15, "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml", "/word/styles.xml"),
            new DocxPartEntry(16, "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml", "/word/numbering.xml"),
            new DocxPartEntry(17, "application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml", "/word/fontTable.xml"),
            new DocxPartEntry(18, "application/vnd.openxmlformats-package.relationships+xml", "/customXml/_rels/item%d.xml.rels"),
            new DocxPartEntry(19, "application/xml", "/customXml/item%d.xml"),
            new DocxPartEntry(20, "application/vnd.openxmlformats-officedocument.customXmlProperties+xml", "/customXml/itemProps%d.xml"),
            new DocxPartEntry(21, "application/vnd.openxmlformats-officedocument.theme+xml", "/word/theme/theme%d.xml"),
            new DocxPartEntry(22, "application/vnd.ms-word.stylesWithEffects+xml", "/word/stylesWithEffects.xml"),
    };

    // these are attributes that need sorting
    //pkg:xmlData
    //    cp:coreProperties
    //xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
    //
    //pkg:xmlData
    //    properties:Properties
    //xmlns:properties="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
    //
    //pkg:xmlData
    //    w:hdr mc:Ignorable="w14 w15 w16se w16cid wp14"
    //    w:ftr mc:Ignorable="w14 w15 w16se w16cid wp14"
    //    w:footnotes
    //    w:endnotes
    //    w:document
    //    w:settings mc:Ignorable=" w15"
    //    w:webSettings
    //    w:styles
    //    w:numbering
    //    w:fonts
    //    a:theme name="Larissa-Design"
    //
    @SafeVarargs
    static <T> Map<T, T> mapOf(T... keysValues) {
        HashMap<T, T> map = new HashMap<>();
        int iMax = keysValues.length;
        for (int i = 0; i < iMax; i++) {
            T key = keysValues[i++];
            if (i < iMax) {
                map.put(key, keysValues[i]);
            } else {
                map.put(key, null);
            }
        }
        return map;
    }

    @SafeVarargs
    static <T> T[] arrayOf(T... content) {
        return content;
    }

    static final String XMLNS_PKG = "http://schemas.microsoft.com/office/2006/xmlPackage";
    static final String XMLNS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final String XMLNS_EXTENDED_PROPERTIES = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";
    static final String XMLNS_DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final String XMLNS_CORE_PROPERTIES = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
    static final String XMLNS_WORDPROCESSING = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    static final HashMap<String, String> sortXmlDataAttributes = new HashMap<>(mapOf(
            "theme", XMLNS_DRAWING,
            "coreProperties", XMLNS_CORE_PROPERTIES,
            "document", XMLNS_WORDPROCESSING,
            "endnotes", XMLNS_WORDPROCESSING,
            "fonts", XMLNS_WORDPROCESSING,
            "footnotes", XMLNS_WORDPROCESSING,
            "ftr", XMLNS_WORDPROCESSING,
            "hdr", XMLNS_WORDPROCESSING,
            "numbering", XMLNS_WORDPROCESSING,
            "settings", XMLNS_WORDPROCESSING,
            "styles", XMLNS_WORDPROCESSING,
            "webSettings", XMLNS_WORDPROCESSING
    ));

    static String xmlnsPrefix(Node node, final String xmlns) {
        String xmlnsPrefix = "";
        if (xmlns != null && !xmlns.isEmpty()) {
            xmlnsPrefix = forAllAttributesUntil(node, null, null, null, new Function<Node, String>() {
                @Override
                public String apply(final Node a) {
                    if (a.getNodeName().startsWith("xmlns:") || a.getNodeName().startsWith("xmlns:")) {
                        if (a.getNodeValue().equals(xmlns)) {
                            // we have it
                            return a.getNodeName().startsWith("xmlns:") ? a.getNodeName().substring("xmlns:".length()) + ":" : "";
                        }
                    }
                    return null;
                }
            });

            if (xmlnsPrefix == null) {
                // search in parent
                xmlnsPrefix = forAllParentsUntil(node, "", new Function<Node, String>() {
                    @Override
                    public String apply(final Node p) {
                        return forAllAttributesUntil(p, null, null, null, new Function<Node, String>() {
                            @Override
                            public String apply(final Node a) {
                                if (a.getNodeName().startsWith("xmlns:") || a.getNodeName().startsWith("xmlns:")) {
                                    if (a.getNodeValue().equals(xmlns)) {
                                        // we have it
                                        return a.getNodeName().startsWith("xmlns:") ? a.getNodeName().substring("xmlns:".length()) + ":" : "";
                                    }
                                }
                                return null;
                            }
                        });
                    }
                });
            }
        }
        return xmlnsPrefix;
    }

    static void forAllParents(final Node node, final Consumer<Node> consumer) {
        forAllParentsUntil(node, null, new Function<Node, Object>() {
            @Override
            public Object apply(final Node n) {
                consumer.accept(n);
                return null;
            }
        });
    }

    static <T> T forAllParentsUntil(final Node node, final T defaultValue, final Function<Node, T> function) {
        Node parentNode = node.getParentNode();

        while (parentNode != null) {
            // pass all children
            T result = function.apply(parentNode);
            if (result != null) {
                return result;
            }
            parentNode = parentNode.getParentNode();
        }
        return defaultValue;
    }

    static void forAllChildren(final Node node, final Consumer<Node> consumer) {
        forAllChildren(node, null, null, consumer);
    }

    static void forAllChildren(final Node node, final String xmlns, final String[] tagNames, final Consumer<Node> consumer) {
        forAllChildrenUntil(node, null, xmlns, tagNames, new Function<Node, Object>() {
            @Override
            public Object apply(final Node n) {
                consumer.accept(n);
                return null;
            }
        });
    }

    static <T> T forAllChildrenUntil(final Node node, final Function<Node, T> function) {
        return forAllChildrenUntil(node, null, null, null, function);
    }

    static <T> T forAllChildrenUntil(final Node node, final T defaultValue, final Function<Node, T> function) {
        return forAllChildrenUntil(node, defaultValue, null, null, function);
    }

    static <T> T forAllChildrenUntil(final Node node, final String xmlns, final String[] tagNames, final Function<Node, T> function) {
        return forAllChildrenUntil(node, null, xmlns, tagNames, function);
    }

    static <T> T forAllChildrenUntil(final Node node, final T defaultValue, final String xmlns, final String[] tagNames, final Function<Node, T> function) {
        if (node.hasChildNodes()) {
            final NodeList childNodes = node.getChildNodes();
            int iMax = childNodes.getLength();
            for (int i = 0; i < iMax; i++) {
                Node child = childNodes.item(i);
                if (tagNames == null || tagNames.length == 0) {
                    // pass all children
                    T result = function.apply(child);
                    if (result != null) {
                        return result;
                    }
                } else {
                    for (String tagName : tagNames) {
                        String xmlnsPrefix = xmlnsPrefix(child, xmlns);
                        if (child.getNodeName().equals(xmlnsPrefix + tagName)) {
                            T result = function.apply(child);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return defaultValue;
    }

    static void forAllAttributes(final Node node, final Consumer<Node> consumer) {
        forAllAttributes(node, null, null, consumer);
    }

    static void forAllAttributes(final Node node, final String xmlns, final String[] attributeNames, final Consumer<Node> consumer) {
        forAllAttributesUntil(node, null, null, attributeNames, new Function<Node, Object>() {
            @Override
            public Object apply(final Node n) {
                consumer.accept(n);
                return null;
            }
        });
    }

    static <T> T forAllAttributesUntil(final Node node, final Function<Node, T> function) {
        return forAllChildrenUntil(node, null, null, null, function);
    }

    static <T> T forAllAttributesUntil(final Node node, final T defaultValue, final Function<Node, T> function) {
        return forAllChildrenUntil(node, defaultValue, null, null, function);
    }

    static <T> T forAllAttributesUntil(final Node node, final T defaultValue, final String xmlns, final String[] attributeNames, final Function<Node, T> function) {
        if (node.hasAttributes()) {
            final String xmlnsPrefix = xmlnsPrefix(node, xmlns);
            final NamedNodeMap attributes = node.getAttributes();
            int iMax = attributes.getLength();
            for (int i = 0; i < iMax; i++) {
                Node item = attributes.item(i);
                if (attributeNames == null || attributeNames.length == 0) {
                    // pass all children
                    T result = function.apply(item);
                    if (result != null) {
                        return result;
                    }
                } else {
                    for (String attributeName : attributeNames) {
                        if (item.getNodeName().equals(xmlnsPrefix + attributeName)) {
                            T result = function.apply(item);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return defaultValue;
    }

/*
    static  class Pair<F, S> {
        private final F first;
        private final S second;

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }

        public Pair(final F first, final S second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "first=" + first +
                    ", second=" + second +
                    '}';
        }
    }
*/

    static Pair<String, String> getXmlnsName(final String xmlnsName) {
        int pos = xmlnsName.indexOf(":");
        if (pos >= 0) {
            return new Pair<>(xmlnsName.substring(0, pos + 1), xmlnsName.substring(pos + 1));
        } else {
            return new Pair<>("", xmlnsName);
        }
    }

    static void sortNodeAttributes(final Node node) {
        forAllChildrenUntil(node, XMLNS_PKG, arrayOf("xmlData"), new Function<Node, Boolean>() {
            @Override
            public Boolean apply(final Node n) {
                forAllChildrenUntil(n, new Function<Node, Boolean>() {
                    @Override
                    public Boolean apply(final Node c) {
                        final Pair<String, String> xmlnsName = getXmlnsName(c.getNodeName());
                        if (sortXmlDataAttributes.containsKey(xmlnsName.getFirst())) {
                            String xmlnsPrefix = xmlnsPrefix(node, sortXmlDataAttributes.get(xmlnsName.getFirst()));

                            if (xmlnsPrefix.equals(xmlnsName.getSecond())) {
                                // our node, we sort attributes of these by name, without the xmlns
                                final ArrayList<Node> toSort = new ArrayList<>();

                                forAllAttributes(c, new Consumer<Node>() {
                                    @Override
                                    public void accept(final Node e) {
                                        toSort.add(e);
                                    }
                                });

                                for (Node item : toSort) {
                                    c.getAttributes().removeNamedItem(item.getNodeName());
                                }
                                Collections.sort(toSort, new Comparator<Node>() {
                                    @Override
                                    public int compare(final Node o1, final Node o2) {
                                        return getXmlnsName(o1.getNodeName()).getSecond().compareTo(getXmlnsName(o2.getNodeName()).getSecond());
                                    }
                                });
                                for (Node item : toSort) {
                                    c.getAttributes().setNamedItem(item);
                                }

                                // only process the first node
                                return true;
                            }
                        }
                        return null;
                    }
                });
                // only process the first node
                return true;
            }
        });
    }

    static void sortRelationships(Node node) {
        forAllChildrenUntil(node, XMLNS_PKG, arrayOf("xmlData"), new Function<Node, Boolean>() {
            @Override
            public Boolean apply(final Node n) {
                forAllChildrenUntil(n, XMLNS_RELATIONSHIPS, arrayOf("Relationships"), new Function<Node, Boolean>() {
                    @Override
                    public Boolean apply(final Node c) {
                        if (c.hasAttributes()) {
                            // we sort Relationship children by Target attribute
                            final ArrayList<Node> toSort = new ArrayList<>();
                            forAllChildren(c, XMLNS_RELATIONSHIPS, arrayOf("Relationship"), new Consumer<Node>() {
                                @Override
                                public void accept(final Node r) {
                                    if (r.hasAttributes() && r.getAttributes().getNamedItem("Target") != null) {
                                        toSort.add(r);
                                    }
                                }
                            });

                            for (Node item : toSort) {
                                c.removeChild(item);
                            }

                            Collections.sort(toSort, new Comparator<Node>() {
                                @Override
                                public int compare(final Node o1, final Node o2) {
                                    return o1.getAttributes().getNamedItem("Target").getNodeValue().compareTo(o2.getAttributes().getNamedItem("Target").getNodeValue());
                                }
                            });
                            for (Node item : toSort) {
                                c.appendChild(item);
                            }
                        }
                        // only process the first node
                        return true;
                    }
                });

                // only process the first node
                return true;
            }
        });
    }

    static void sortProperties(Node node) {
        forAllChildrenUntil(node, XMLNS_PKG, arrayOf("xmlData"), new Function<Node, Boolean>() {
            @Override
            public Boolean apply(final Node n) {
                forAllChildrenUntil(n, XMLNS_EXTENDED_PROPERTIES, arrayOf("Properties"), new Function<Node, Boolean>() {
                    @Override
                    public Boolean apply(final Node p) {
                        // we sort properties
                        final ArrayList<Node> toSort = new ArrayList<>();
                        forAllChildren(p, new Consumer<Node>() {
                            @Override
                            public void accept(final Node e) {
                                toSort.add(e);
                            }
                        });
                        if (!toSort.isEmpty()) {
                            for (Node item : toSort) {
                                p.removeChild(item);
                            }
                            Collections.sort(toSort, new Comparator<Node>() {
                                @Override
                                public int compare(final Node o1, final Node o2) {
                                    return getXmlnsName(o1.getNodeName()).getSecond().compareTo(getXmlnsName(o2.getNodeName()).getSecond());
                                }
                            });
                            for (Node item : toSort) {
                                p.appendChild(item);
                            }
                        }

                        // only process the first node
                        return true;
                    }
                });

                // only process the first node
                return true;
            }
        });
    }

    public static String sortDocumentParts(String xml) {
        try {
            final InputSource src = new InputSource(new StringReader(xml));
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(false);

            final Document document = builderFactory.newDocumentBuilder().parse(src);

            final DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            final DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
            final LSSerializer writer = impl.createLSSerializer();

            writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE); // Set this to true if the output needs to be beautified.
            writer.getDomConfig().setParameter("xml-declaration", false);

            final HashMap<String, HashMap<Pattern, DocxPartEntry>> contentTypeNameEntry = new HashMap<String, HashMap<Pattern, DocxPartEntry>>();
            for (DocxPartEntry entry : entries) {
                HashMap<Pattern, DocxPartEntry> entryHashMap = contentTypeNameEntry.get(entry.contentType);
                if (entryHashMap == null) {
                    entryHashMap = new HashMap<>();
                    contentTypeNameEntry.put(entry.contentType, entryHashMap);
                }
                entryHashMap.put(entry.regex, entry);
            }

            final DocxPartEntry unknownPartEntry = new DocxPartEntry(99, "", "");

            final ArrayList<DocxPartEntry> partEntries = new ArrayList<>();
            final int[] unknownIndex = new int[] { 0 };
            //final NodeList parts = document.getElementsByTagName("pkg:part");

            final StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

            forAllChildren(document, XMLNS_PKG, arrayOf("package"), new Consumer<Node>() {
                @Override
                public void accept(final Node pkg) {
                    forAllChildren(pkg, XMLNS_PKG, arrayOf("part"), new Consumer<Node>() {
                        @Override
                        public void accept(final Node part) {
                            sortNodeAttributes(part);
                            sortRelationships(part);
                            sortProperties(part);

                            final NamedNodeMap attributes = part.getAttributes();
                            final Node contentTypeNode = attributes.getNamedItem("pkg:contentType");

                            boolean handled = false;
                            if (contentTypeNode != null) {
                                String contentType = contentTypeNode.getNodeValue();

                                final HashMap<Pattern, DocxPartEntry> entryHashMap = contentTypeNameEntry.get(contentType);
                                if (entryHashMap != null) {
                                    final Node nameNode = attributes.getNamedItem("pkg:name");
                                    // now we find the entry for these
                                    if (nameNode != null) {
                                        String name = nameNode.getNodeValue();
                                        for (Entry<Pattern, DocxPartEntry> entry : entryHashMap.entrySet()) {
                                            Matcher matcher = entry.getKey().matcher(name);
                                            if (matcher.matches()) {
                                                int index = matcher.groupCount() > 0 ? Integer.parseInt(matcher.group(1)) : 0;
                                                partEntries.add(new DocxPartEntry(entry.getValue(), part, index));
                                                handled = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            if (!handled) {
                                // make it unknown
                                partEntries.add(new DocxPartEntry(unknownPartEntry, part, ++unknownIndex[0]));
                            }
                        }
                    });

                    for (DocxPartEntry pe : partEntries) {
                        pkg.removeChild(pe.node);
                    }
                    Collections.sort(partEntries, new Comparator<DocxPartEntry>() {
                        @Override
                        public int compare(final DocxPartEntry o1, final DocxPartEntry o2) {
                            final int ordinals = Integer.compare(o1.ordinal, o2.ordinal);
                            return ordinals != 0 ? ordinals : Integer.compare(o1.index, o2.index);
                        }
                    });
                    for (DocxPartEntry pe : partEntries) {
                        pkg.appendChild(pe.node);
                    }
                    sb.append(writer.writeToString(pkg));
                }
            });

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
