package nrv.usb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses NRV SDK bias settings text files (//@ headers, slv:addr=val, wait N).
 *
 * @see https://nrv.kr/
 */
public class NRVSettingsParser {

    public static final String VERSION_HEADER = "//@ DVS_VERSION S5KRC1S";
    public static final String DESCRIPTION_PREFIX = "//@ Description ";

    public static class ParseResult {
        private final List<NRVRegisterSetting> settings;
        private final String description;

        public ParseResult(List<NRVRegisterSetting> settings, String description) {
            this.settings = settings;
            this.description = description == null ? "" : description;
        }

        public List<NRVRegisterSetting> getSettings() {
            return settings;
        }

        public String getDescription() {
            return description;
        }
    }

    public static ParseResult parseFile(File file) throws IOException {
        final List<NRVRegisterSetting> settings = new ArrayList<>();
        String description = "";
        boolean sawVersion = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = trim(line);
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("//@")) {
                    if (VERSION_HEADER.equals(trimmed)) {
                        sawVersion = true;
                    } else if (trimmed.startsWith(DESCRIPTION_PREFIX)) {
                        description = trimmed.substring(DESCRIPTION_PREFIX.length()).trim();
                    }
                    continue;
                }
                if (trimmed.startsWith("//")) {
                    continue;
                }

                final int[] parsed = parseStringValues(trimmed);
                final int slvAddr = parsed[0];
                final int adr = parsed[1];
                final int val = parsed[2];
                final int parseMode = parsed[3];

                String comment = "";
                final int slash = trimmed.indexOf("//");
                if (slash >= 0) {
                    comment = trimmed.substring(slash + 2).trim();
                }

                switch (parseMode) {
                    case 1:
                        settings.add(new NRVRegisterSetting(slvAddr, adr, val, comment));
                        break;
                    case 2:
                        settings.add(NRVRegisterSetting.waitSetting(val, comment));
                        break;
                    default:
                        break;
                }
            }
        }

        if (!sawVersion) {
            throw new IOException("Not a valid NRV bias file: missing " + VERSION_HEADER);
        }
        return new ParseResult(settings, description);
    }

    /**
     * Port of Utils.cpp parseString. Returns parse mode:
     * 1 = I2C write complete, 2 = wait, -1 = invalid/comment.
     */
    public static int parseString(String s, int slvAddr, int adr, int val) {
        return parseStringValues(s)[3];
    }

    private static int[] parseStringValues(String s) {
        int slvAddr = 0;
        int adr = 0;
        int val = 0;
        int mode = 10;
        int dstIndex = 0;
        int i = 0;

        while (i < s.length()) {
            char tmp = Character.toLowerCase(s.charAt(i));
            if (tmp == 0 || tmp == '\n' || tmp == '/') {
                return new int[]{slvAddr, adr, val, mode};
            } else if (tmp == ' ' || tmp == '\t' || tmp == ':' || tmp == '=') {
                i++;
                continue;
            } else if (tmp == 'w') {
                if (i + 3 < s.length()
                        && Character.toLowerCase(s.charAt(i + 1)) == 'a'
                        && Character.toLowerCase(s.charAt(i + 2)) == 'i'
                        && Character.toLowerCase(s.charAt(i + 3)) == 't') {
                    i += 3;
                    mode = 2;
                    int[] pos = new int[]{i + 1};
                    val = atoi(s, pos);
                    i = pos[0];
                    continue;
                } else {
                    return new int[]{slvAddr, adr, val, -1};
                }
            } else if (isHexDigit(tmp)) {
                int[] pos = new int[]{i};
                int parsed = htoi(s, pos);
                i = pos[0];
                switch (dstIndex) {
                    case 0:
                        slvAddr = parsed;
                        break;
                    case 1:
                        adr = parsed;
                        break;
                    case 2:
                        val = parsed;
                        break;
                    default:
                        break;
                }
                switch (mode) {
                    case 10:
                        mode = 11;
                        dstIndex = 1;
                        break;
                    case 11:
                        mode = 12;
                        dstIndex = 2;
                        break;
                    case 12:
                        mode = 1;
                        break;
                    default:
                        break;
                }
            } else {
                return new int[]{slvAddr, adr, val, -1};
            }
            i++;
        }
        return new int[]{slvAddr, adr, val, mode};
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int htoi(String s, int[] indexHolder) {
        int n = 0;
        int i = indexHolder[0];
        while (i < s.length()) {
            char c = s.charAt(i);
            int hexdigit;
            if (c >= '0' && c <= '9') {
                hexdigit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                hexdigit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                hexdigit = c - 'A' + 10;
            } else {
                break;
            }
            n = 16 * n + hexdigit;
            i++;
        }
        indexHolder[0] = i;
        return n;
    }

    private static int atoi(String s, int[] indexHolder) {
        int n = 0;
        int i = indexHolder[0];
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                n = 10 * n + (c - '0');
                i++;
            } else {
                break;
            }
        }
        indexHolder[0] = i;
        return n;
    }

    private static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }
}
