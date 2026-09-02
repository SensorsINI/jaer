package net.sf.jaer.eventio.aedat4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.RecordingChipDetector;

/**
 * Maps selected AEDAT-4 EVTS streams onto existing AEViewer windows.
 * Chip class first; USB serial / bus-addr only when two or more streams
 * resolve to the same AEChip. Leftover windows are reused (chip change);
 * new windows only if there are not enough viewers. Soft cap is
 * {@link #SOFT_MAX_VIEWERS} unless the recording has more cameras.
 */
public final class Aedat4PlaybackAssignment {

    public static final int SOFT_MAX_VIEWERS = 8;

    private static final Pattern BUS_ADDR = Pattern.compile("(?:us|bus)?(\\d+)addr(\\d+)");
    private static final Pattern PREFS_BUS_ADDR = Pattern.compile("b(\\d+)a(\\d+)");

    private Aedat4PlaybackAssignment() {
    }

    /** One open viewer as a matching slot (no Swing). */
    public static final class ViewerSlot {
        public final int index;
        public final String chipSimpleName;
        /** USB serial, {@code busN-addrM}, or empty. */
        public final String identity;

        public ViewerSlot(int index, String chipSimpleName, String identity) {
            this.index = index;
            this.chipSimpleName = chipSimpleName;
            this.identity = identity == null ? "" : identity;
        }
    }

    /** One selected stream bound to an existing viewer or a new window. */
    public static final class Binding {
        public final RecordingChipDetector.StreamHint stream;
        public final Class<? extends AEChip> chip;
        /** Existing viewer index, or {@code -1} when {@link #createNew}. */
        public final int viewerIndex;
        public final boolean createNew;
        public final boolean changeChip;

        public Binding(RecordingChipDetector.StreamHint stream, Class<? extends AEChip> chip,
                int viewerIndex, boolean createNew, boolean changeChip) {
            this.stream = stream;
            this.chip = chip;
            this.viewerIndex = viewerIndex;
            this.createNew = createNew;
            this.changeChip = changeChip;
        }
    }

    public static int maxViewers(int deviceCount) {
        return Math.max(SOFT_MAX_VIEWERS, Math.max(0, deviceCount));
    }

    /**
     * Assign {@code streams} to {@code viewers} in stream order. {@code used}
     * marks viewers already taken (the opening window after it has a stream).
     * {@code used} may be null.
     */
    public static List<Binding> assign(
            List<RecordingChipDetector.StreamHint> streams,
            List<ViewerSlot> viewers,
            List<Class<? extends AEChip>> loaded,
            boolean[] used) {
        List<Binding> out = new ArrayList<>();
        if (streams == null || streams.isEmpty()) {
            return out;
        }
        List<ViewerSlot> slots = viewers == null ? List.of() : viewers;
        boolean[] taken = used == null ? new boolean[slots.size()] : used;
        if (taken.length < slots.size()) {
            boolean[] grow = new boolean[slots.size()];
            System.arraycopy(taken, 0, grow, 0, taken.length);
            taken = grow;
        }
        Class<? extends AEChip>[] chips = new Class[streams.size()];
        Map<String, Integer> chipCount = new HashMap<>();
        for (int i = 0; i < streams.size(); i++) {
            chips[i] = RecordingChipDetector.resolve(streams.get(i).toChipHint(), loaded);
            if (chips[i] != null) {
                String key = chips[i].getName();
                chipCount.put(key, chipCount.getOrDefault(key, 0) + 1);
            }
        }
        int[] viewerForStream = new int[streams.size()];
        boolean[] create = new boolean[streams.size()];
        boolean[] bound = new boolean[streams.size()];
        for (int i = 0; i < streams.size(); i++) {
            viewerForStream[i] = -1;
        }

        for (int i = 0; i < streams.size(); i++) {
            Class<? extends AEChip> want = chips[i];
            if (want == null) {
                continue;
            }
            boolean needIdentity = chipCount.getOrDefault(want.getName(), 0) > 1;
            String streamId = identityFromSource(streams.get(i).source);
            int chosen = -1;
            if (needIdentity && !streamId.isEmpty()) {
                chosen = firstMatchingChip(slots, taken, want, streamId, true);
            }
            if (chosen < 0) {
                chosen = firstMatchingChip(slots, taken, want, null, false);
            }
            if (chosen >= 0) {
                taken[chosen] = true;
                viewerForStream[i] = chosen;
                bound[i] = true;
            }
        }

        for (int i = 0; i < streams.size(); i++) {
            if (bound[i]) {
                continue;
            }
            int leftover = firstUnused(taken, slots.size());
            if (leftover >= 0) {
                taken[leftover] = true;
                viewerForStream[i] = leftover;
                bound[i] = true;
            }
        }

        int created = 0;
        int max = maxViewers(streams.size());
        for (int i = 0; i < streams.size(); i++) {
            if (bound[i]) {
                continue;
            }
            if (slots.size() + created < max) {
                create[i] = true;
                bound[i] = true;
                created++;
            }
        }

        for (int i = 0; i < streams.size(); i++) {
            int vi = viewerForStream[i];
            boolean isNew = create[i];
            boolean change = false;
            if (!isNew && vi >= 0) {
                change = !sameChip(chips[i], slots.get(vi).chipSimpleName);
            }
            out.add(new Binding(streams.get(i), chips[i], isNew ? -1 : vi, isNew, change));
        }
        return out;
    }

    public static List<Binding> assign(
            List<RecordingChipDetector.StreamHint> streams,
            List<ViewerSlot> viewers,
            List<Class<? extends AEChip>> loaded) {
        return assign(streams, viewers, loaded, null);
    }

    public static Binding bindingForViewer(List<Binding> plan, int viewerIndex) {
        if (plan == null || viewerIndex < 0) {
            return null;
        }
        for (Binding b : plan) {
            if (!b.createNew && b.viewerIndex == viewerIndex) {
                return b;
            }
        }
        return null;
    }

    public static String identityFromSource(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int us = source.lastIndexOf('_');
        int dash = source.lastIndexOf('-');
        int sep = Math.max(us, dash);
        if (sep <= 0 || sep == source.length() - 1) {
            return "";
        }
        return source.substring(sep + 1);
    }

    public static boolean identitiesMatch(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        String na = normalizeIdentity(a);
        String nb = normalizeIdentity(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb) || na.contains(nb) || nb.contains(na)) {
            return true;
        }
        int[] pa = parseBusAddr(na);
        int[] pb = parseBusAddr(nb);
        return pa != null && pb != null && pa[0] == pb[0] && pa[1] == pb[1];
    }

    static String normalizeIdentity(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static int[] parseBusAddr(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Matcher m = BUS_ADDR.matcher(normalized);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        Matcher p = PREFS_BUS_ADDR.matcher(normalized);
        if (p.find()) {
            return new int[]{Integer.parseInt(p.group(1)), Integer.parseInt(p.group(2))};
        }
        return null;
    }

    private static boolean sameChip(Class<? extends AEChip> want, String simpleName) {
        return want != null && simpleName != null && !simpleName.isEmpty()
                && want.getSimpleName().equalsIgnoreCase(simpleName);
    }

    private static int firstMatchingChip(List<ViewerSlot> slots, boolean[] taken,
            Class<? extends AEChip> want, String streamIdentity, boolean requireIdentity) {
        for (int i = 0; i < slots.size(); i++) {
            if (taken[i] || !sameChip(want, slots.get(i).chipSimpleName)) {
                continue;
            }
            if (requireIdentity && !identitiesMatch(streamIdentity, slots.get(i).identity)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int firstUnused(boolean[] taken, int n) {
        for (int i = 0; i < n; i++) {
            if (!taken[i]) {
                return i;
            }
        }
        return -1;
    }
}
