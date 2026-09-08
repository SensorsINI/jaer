package net.sf.jaer.util;

import java.awt.FontMetrics;
import java.io.File;
import java.util.Locale;

/**
 * Save-dialog filename helpers: force a written extension, and shorten long
 * paths for display (keep start and end, insert an ellipsis).
 */
public final class OutputFilename {

    private OutputFilename() {
    }

    /**
     * Force {@code file} to {@code preferredExt} unless its suffix is already
     * in {@code accepted} (no leading dots). Same rules as Save As: add a
     * missing suffix, insert a forgotten dot ({@code 1mp4} → {@code 1.mp4}),
     * replace any other suffix.
     */
    public static File ensureExtension(File file, String preferredExt, String... accepted) {
        String preferred = stripDot(preferredExt);
        if (preferred.isEmpty()) {
            preferred = "dat";
        }
        if (file == null) {
            return new File("jAER-export." + preferred);
        }
        String name = file.getName();
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "jAER-export." + preferred;
            File parent = file.getParentFile();
            return parent != null ? new File(parent, name) : new File(name);
        }
        String fixed = ensureExtensionName(name, preferred, accepted);
        if (fixed.equals(name)) {
            return file;
        }
        File parent = file.getParentFile();
        return parent != null ? new File(parent, fixed) : new File(fixed);
    }

    public static String ensureExtensionName(String name, String preferredExt, String... accepted) {
        String preferred = stripDot(preferredExt);
        if (preferred.isEmpty()) {
            preferred = "dat";
        }
        String[] acc = normalizeAccepted(preferred, accepted);
        if (name == null || name.isEmpty()) {
            return "jAER-export." + preferred;
        }
        String current = name;
        while (true) {
            int dot = current.lastIndexOf('.');
            if (dot > 0 && dot < current.length() - 1) {
                String ext = current.substring(dot + 1);
                if (accepts(acc, ext)) {
                    return current.substring(0, dot) + "." + ext.toLowerCase(Locale.ROOT);
                }
                current = current.substring(0, dot);
                continue;
            }
            String glued = gluedAcceptedExtension(current, acc);
            if (glued != null) {
                return current.substring(0, current.length() - glued.length()) + "." + glued;
            }
            while (current.endsWith(".") && current.length() > 1) {
                current = current.substring(0, current.length() - 1);
            }
            return current + "." + preferred;
        }
    }

    private static String[] normalizeAccepted(String preferred, String[] accepted) {
        if (accepted == null || accepted.length == 0) {
            return new String[]{preferred};
        }
        String[] out = new String[accepted.length];
        for (int i = 0; i < accepted.length; i++) {
            String a = stripDot(accepted[i]);
            out[i] = a.isEmpty() ? preferred : a;
        }
        return out;
    }

    private static boolean accepts(String[] accepted, String ext) {
        if (ext == null || ext.isEmpty()) {
            return false;
        }
        String e = stripDot(ext);
        for (String a : accepted) {
            if (a.equalsIgnoreCase(e)) {
                return true;
            }
        }
        return false;
    }

    private static String gluedAcceptedExtension(String name, String[] accepted) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : accepted) {
            if (ext.isEmpty() || lower.length() <= ext.length()) {
                continue;
            }
            if (lower.endsWith(ext) && name.charAt(name.length() - ext.length() - 1) != '.') {
                return ext;
            }
        }
        return null;
    }

    private static String stripDot(String ext) {
        if (ext == null) {
            return "";
        }
        String e = ext.trim();
        while (e.startsWith(".")) {
            e = e.substring(1);
        }
        return e.toLowerCase(Locale.ROOT);
    }

    /**
     * Shorten {@code text} to about {@code maxChars}, keeping the start and end.
     */
    public static String ellipsizeMiddle(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars < 8 || text.length() <= maxChars) {
            return text;
        }
        int keepEnd = Math.max(12, maxChars / 3);
        int keepStart = maxChars - keepEnd - 1;
        if (keepStart < 4) {
            keepStart = 4;
            keepEnd = maxChars - keepStart - 1;
        }
        if (keepStart + 1 + keepEnd >= text.length()) {
            return text;
        }
        return text.substring(0, keepStart) + "…" + text.substring(text.length() - keepEnd);
    }

    /**
     * Shorten {@code text} to fit {@code maxWidth} pixels, keeping more of the
     * end (filename / last folders).
     */
    public static String ellipsizeMiddle(String text, FontMetrics fm, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (fm == null || maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ell = "…";
        int ellW = fm.stringWidth(ell);
        int budget = maxWidth - ellW;
        if (budget <= 0) {
            return ell;
        }
        int endChars = 0;
        int endW = 0;
        int endBudget = Math.max(budget / 2, budget - fm.charWidth('W') * 8);
        for (int i = text.length() - 1; i >= 0; i--) {
            int cw = fm.charWidth(text.charAt(i));
            if (endW + cw > endBudget) {
                break;
            }
            endW += cw;
            endChars++;
        }
        int startBudget = budget - endW;
        int startChars = 0;
        int startW = 0;
        int limit = text.length() - endChars;
        for (int i = 0; i < limit; i++) {
            int cw = fm.charWidth(text.charAt(i));
            if (startW + cw > startBudget) {
                break;
            }
            startW += cw;
            startChars++;
        }
        if (startChars + endChars >= text.length()) {
            return text;
        }
        if (endChars == 0 && startChars == 0) {
            return ell;
        }
        return text.substring(0, startChars) + ell + text.substring(text.length() - endChars);
    }
}
