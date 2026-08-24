package net.sf.jaer.eventio;

/**
 * Audited, symmetric codec for encoding preference keys/values so they can be
 * embedded safely on a single physical line of a legacy AEDAT header.
 *
 * <p>The legacy header writer previously wrote preference values verbatim. A
 * value containing {@code < > & "} or a line break could therefore inject a
 * fake header line or a second {@code <entry>} element. Every key and value is
 * escaped here (see {@link #escape(java.lang.String)}) so the recorded header
 * stays one physical line per entry and cannot be split by a hostile value.
 *
 * <p>This is deliberately a <em>line-oriented legacy codec</em>, not a general
 * XML parser: {@link #parseEntryLine(java.lang.String)} accepts only the exact
 * {@code #<entry key="..." value="..."/>} shape this class emits, decodes the
 * two double-quoted attributes, and unescapes them via
 * {@link #unescape(java.lang.String)}. Anything else is rejected rather than
 * interpreted, so a malformed value can never inject a fake header line or a
 * second element.
 */
public final class SnapshotCodec {

    private SnapshotCodec() {
    }

    /**
     * Escape a string for safe inclusion inside a double-quoted XML attribute on
     * a single physical line.
     *
     * <ul>
     *   <li>{@code &} {@code ->} {@code &amp;}</li>
     *   <li>{@code <} {@code ->} {@code &lt;}</li>
     *   <li>{@code >} {@code ->} {@code &gt;}</li>
     *   <li>{@code "} {@code ->} {@code &quot;}</li>
     *   <li>{@code \r} {@code ->} {@code &#13;}</li>
     *   <li>{@code \n} {@code ->} {@code &#10;}</li>
     * </ul>
     *
     * @param s the value to escape; {@code null} yields the empty string
     * @return the escaped value
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\r':
                    sb.append("&#13;");
                    break;
                case '\n':
                    sb.append("&#10;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Inverse of {@link #escape(java.lang.String)}: turn the entity/character
     * references this codec emits back into their literal characters.
     *
     * @param s the escaped string; {@code null} yields the empty string
     * @return the unescaped string
     */
    public static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi < 0 || semi - i > 6) { // longest entity we emit is "&amp;" (5 chars); anything longer is not ours
                    // Not one of our entities: keep the ampersand verbatim rather than guess.
                    sb.append(c);
                    i++;
                    continue;
                }
                String ent = s.substring(i, semi + 1);
                switch (ent) {
                    case "&amp;":
                        sb.append('&');
                        i = semi + 1;
                        break;
                    case "&lt;":
                        sb.append('<');
                        i = semi + 1;
                        break;
                    case "&gt;":
                        sb.append('>');
                        i = semi + 1;
                        break;
                    case "&quot;":
                        sb.append('"');
                        i = semi + 1;
                        break;
                    case "&#13;":
                        sb.append('\r');
                        i = semi + 1;
                        break;
                    case "&#10;":
                        sb.append('\n');
                        i = semi + 1;
                        break;
                    default:
                        // Unknown entity: keep verbatim (we only ever decode entities we emitted).
                        sb.append(c);
                        i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Tightly-constrained parser for one legacy entry line of the exact shape
     * this codec emits: {@code #<entry key="KEY" value="VALUE"/>}. The comment
     * prefix ({@value net.sf.jaer.eventio.AEDataFile#COMMENT_CHAR}) and the
     * self-closing {@code <entry .../>} element are required. The two attribute
     * values are unescaped. Any other shape returns {@code null} so callers can
     * skip non-entry header lines, and a value can never smuggle a second
     * {@code <entry>} or a fake line through the parser.
     *
     * @param line one header line (with or without leading comment char)
     * @return the decoded entry, or {@code null} if the line is not an entry line
     */
    public static Entry parseEntryLine(String line) {
        if (line == null) {
            return null;
        }
        String l = line.trim();
        if (l.length() > 0 && l.charAt(0) == AEDataFile.COMMENT_CHAR) {
            l = l.substring(1).trim();
        }
        final String prefix = "<entry key=\"";
        if (!l.startsWith(prefix) || !l.endsWith("\"/>")) {
            return null;
        }
        String rest = l.substring(prefix.length(), l.length() - "\"/>".length());
        // rest is: KEY" value="VALUE  (terminated by the trailing "/>)
        int valueSep = rest.indexOf("\" value=\"");
        if (valueSep < 0) {
            return null;
        }
        String key = rest.substring(0, valueSep);
        String value = rest.substring(valueSep + "\" value=\"".length());
        if (key.indexOf('"') >= 0 || value.indexOf('"') >= 0 || key.isEmpty()) {
            // Must be a well-formed quoted pair; reject anything that could hide a
            // second element or unquoted junk.
            return null;
        }
        return new Entry(unescape(key), unescape(value));
    }

    /**
     * Immutable value object: one preference key/value pair. Non-null fields.
     */
    public static final class Entry {
        private final String key;
        private final String value;

        public Entry(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            this.key = key;
            this.value = value == null ? "" : value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Entry{" + key + "=" + value + "}";
        }
    }
}
