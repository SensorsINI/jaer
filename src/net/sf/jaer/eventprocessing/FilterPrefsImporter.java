/*
 * FilterPrefsImporter.java
 */
package net.sf.jaer.eventprocessing;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Copies matching properties from an exported preferences XML file onto a
 * filter, ignoring the chip and enclosing-filter path stored in the file.
 * Values are written with this filter's own preference key so a later
 * {@link FilterFrame#renewContents()} loads them.
 */
final class FilterPrefsImporter {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Import writes one preference per property. The event thread must not copy that burst. */
    private static final Set<String> IMPORTED = ConcurrentHashMap.newKeySet();

    /** Not filter settings. Inherited from {@link EventFilter}. */
    private static final Set<String> SKIP = Set.of(
            "chip", "prefs", "enclosedFilter", "enclosedFilterChain",
            "selected", "class", "enclosingFilter", "filterPanel");

    /**
     * Present on the enclosing filter, but not the synchronized copies of
     * tracker settings.
     */
    private static final Set<String> DO_NOT_SHARE = Set.of("filterEnabled", "annotationEnabled");

    static final class Result {
        final List<String> lines = new ArrayList<>();
        final Map<EventFilter, LinkedHashSet<String>> shared = new LinkedHashMap<>();
        int total;

        boolean isEmpty() {
            return total == 0;
        }

        String summary(String fileName) {
            StringBuilder sb = new StringBuilder();
            sb.append("Stored matching properties from ").append(fileName).append('.');
            for (String line : lines) {
                sb.append("\n").append(line);
            }
            return sb.toString();
        }
    }

    private static final class ImportedNode {
        final String name;
        final Map<String, String> entries = new LinkedHashMap<>();

        ImportedNode(String name) {
            this.name = name;
        }
    }

    private FilterPrefsImporter() {
    }

    /** True for a key this import just wrote. The event is consumed so it is not copied again. */
    static boolean consumeWrite(Preferences prefs, String key, String value) {
        if (prefs == null || key == null || value == null) {
            return false;
        }
        return IMPORTED.remove(token(prefs, key, value));
    }

    private static void noteWrite(Preferences prefs, String key, String value) {
        IMPORTED.add(token(prefs, key, value));
    }

    private static String token(Preferences prefs, String key, String value) {
        return prefs.absolutePath() + '\0' + key + '\0' + value;
    }

    static Result apply(EventFilter root, File file) throws ParserConfigurationException, SAXException, IOException, IntrospectionException {
        IMPORTED.clear();
        List<ImportedNode> nodes = parse(file);
        Result result = new Result();
        applyTree(root, nodes, result);
        for (Map.Entry<EventFilter, LinkedHashSet<String>> shared : result.shared.entrySet()) {
            int count = shared.getValue().size();
            result.total += count;
            result.lines.add(shared.getKey().getShortName() + ": " + count + " shared");
        }
        return result;
    }

    private static void applyTree(EventFilter filter, List<ImportedNode> nodes, Result result) throws IntrospectionException {
        applyOne(filter, nodes, result);
        EventFilter enclosed = filter.getEnclosedFilter();
        if (enclosed != null) {
            applyTree(enclosed, nodes, result);
        }
        FilterChain chain = filter.getEnclosedFilterChain();
        if (chain != null) {
            for (EventFilter child : chain) {
                applyTree(child, nodes, result);
            }
        }
    }

    private static void applyOne(EventFilter filter, List<ImportedNode> nodes, Result result) throws IntrospectionException {
        ImportedNode node = bestNode(filter, nodes);
        if (node == null) {
            return;
        }
        Set<String> names = propertyNames(filter);
        Preferences prefs = filter.getPrefs();
        String header = filter.prefsKeyHeader();
        int count = 0;
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, String> entry : node.entries.entrySet()) {
            String prop = propertyName(entry.getKey(), node.name, filter);
            if (!names.contains(prop)) {
                skipped.add(prop);
                continue;
            }
            noteWrite(prefs, header + prop, entry.getValue());
            prefs.put(header + prop, entry.getValue());
            writeEnclosing(filter, prop, entry.getValue(), result);
            count++;
            log.fine(String.format("Imported %s=%s onto %s", prop, entry.getValue(), prefs.absolutePath()));
        }
        if (count == 0) {
            return;
        }
        result.total += count;
        StringBuilder line = new StringBuilder();
        line.append(filter.getShortName()).append(": ").append(count);
        if (!skipped.isEmpty()) {
            line.append(" (skipped ").append(String.join(", ", skipped)).append(')');
        }
        result.lines.add(line.toString());
        log.info(String.format("Stored %d properties from node %s onto %s at %s",
                count, node.name, filter.getShortName(), prefs.absolutePath()));
    }

    /**
     * Properties the enclosing filter declares itself (not inherited
     * {@link EventFilter} controls such as {@code filterEnabled}) are the
     * synchronized copies. Overwrite those too.
     */
    private static void writeEnclosing(EventFilter filter, String prop, String value, Result result) throws IntrospectionException {
        EventFilter parent = filter.getEnclosingFilter();
        while (parent != null) {
            if (!DO_NOT_SHARE.contains(prop) && declaredProperties(parent).contains(prop)) {
                parent.getPrefs().put(parent.prefsKeyHeader() + prop, value);
                result.shared.computeIfAbsent(parent, key -> new LinkedHashSet<>()).add(prop);
                log.fine(String.format("Imported shared %s=%s onto %s", prop, value, parent.getShortName()));
            }
            parent = parent.getEnclosingFilter();
        }
    }

    private static ImportedNode bestNode(EventFilter filter, List<ImportedNode> nodes) {
        ImportedNode best = null;
        int bestScore = -1;
        for (ImportedNode node : nodes) {
            int score = score(filter, node.name);
            if (score < 0) {
                continue;
            }
            if (best == null || score > bestScore || (score == bestScore && node.entries.size() > best.entries.size())) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    /** Exact class name scores higher than a concrete superclass name. */
    private static int score(EventFilter filter, String nodeName) {
        Class<?> type = filter.getClass();
        int depth = 0;
        while (type != null && EventFilter.class.isAssignableFrom(type)) {
            if (!Modifier.isAbstract(type.getModifiers()) && type.getSimpleName().equals(nodeName)) {
                return 1000 - depth;
            }
            type = type.getSuperclass();
            depth++;
        }
        return -1;
    }

    private static String propertyName(String key, String nodeName, EventFilter filter) {
        String prefix = nodeName + ".";
        if (key.startsWith(prefix)) {
            return key.substring(prefix.length());
        }
        Class<?> type = filter.getClass();
        while (type != null && EventFilter.class.isAssignableFrom(type)) {
            String classPrefix = type.getSimpleName() + ".";
            if (key.startsWith(classPrefix)) {
                return key.substring(classPrefix.length());
            }
            type = type.getSuperclass();
        }
        int dot = key.indexOf('.');
        if (dot > 0 && dot < key.length() - 1) {
            return key.substring(dot + 1);
        }
        return key;
    }

    private static Set<String> propertyNames(EventFilter filter) throws IntrospectionException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addWritable(Introspector.getBeanInfo(filter.getClass()), names);
        for (EventFilter.PrefsKeyClassValueDefault stored : filter.preferencesMap.values()) {
            if (stored.key() != null && !SKIP.contains(stored.key())) {
                names.add(stored.key());
            }
        }
        return names;
    }

    /** Properties declared on this class, excluding {@link EventFilter2D} and above. */
    private static Set<String> declaredProperties(EventFilter filter) throws IntrospectionException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addWritable(Introspector.getBeanInfo(filter.getClass(), EventFilter2D.class), names);
        return names;
    }

    private static void addWritable(BeanInfo info, Set<String> names) {
        for (PropertyDescriptor property : info.getPropertyDescriptors()) {
            if (property.getWriteMethod() == null || SKIP.contains(property.getName())) {
                continue;
            }
            names.add(property.getName());
        }
    }

    private static List<ImportedNode> parse(File file) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Parser does not support these attributes.
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                return new InputSource(new StringReader(""));
            }
        });
        Element root = builder.parse(file).getDocumentElement();
        List<ImportedNode> nodes = new ArrayList<>();
        collect(root, nodes);
        return nodes;
    }

    private static void collect(Element element, List<ImportedNode> nodes) {
        String tag = element.getTagName();
        if ("node".equals(tag) || "root".equals(tag)) {
            ImportedNode imported = new ImportedNode(element.getAttribute("name"));
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element childElement && "map".equals(childElement.getTagName())) {
                    readEntries(childElement, imported.entries);
                }
            }
            if (!imported.entries.isEmpty()) {
                nodes.add(imported);
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                collect(childElement, nodes);
            }
        }
    }

    private static void readEntries(Element map, Map<String, String> entries) {
        NodeList children = map.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element entry) || !"entry".equals(entry.getTagName())) {
                continue;
            }
            String key = entry.getAttribute("key");
            if (key != null && !key.isEmpty()) {
                entries.put(key, entry.getAttribute("value"));
            }
        }
    }
}
