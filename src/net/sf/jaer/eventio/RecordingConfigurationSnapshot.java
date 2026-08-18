package net.sf.jaer.eventio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.AEChip;

/**
 * An immutable snapshot of a chip's configuration and live bias preferences,
 * captured once at recording start.
 *
 * <p>Rationale: the legacy AEDAT writer previously reread the live Java
 * {@link Preferences} node while writing the header, which (a) reflected
 * preferences that changed after recording began and (b) emitted raw, unescaped
 * {@code <entry>} lines that a value containing {@code & < > "} or a line break
 * could use to inject a fake header line or a second entry. This snapshot freezes
 * the values once and serializes them through {@link SnapshotCodec} so the
 * recorded configuration is deterministic, immutable and escape-safe.
 *
 * <p>Capture semantics ({@link #captureFromChip(AEChip)}):
 * <ol>
 *   <li>A {@code null} chip yields an empty snapshot — sharing code never has to
 *       special-case "no chip" and no writer dereferences a null chip.</li>
 *   <li>If the chip has a bias generator, {@code storePreferences()} is called
 *       exactly once so the live pot/masterbias values are flushed into the
 *       preference node before we copy it. This is the single live-bias flush.</li>
 *   <li>Only the entries on the chip's own preference node
 *       ({@link net.sf.jaer.chip.Chip#getPrefs()}) are copied, in sorted key
 *       order, into immutable {@link SnapshotCodec.Entry} objects. The resulting
 *       snapshot no longer references the live node, so later preference mutation
 *       cannot change what is recorded.</li>
 * </ol>
 *
 * <p>The immutable snapshot is safe to hand to multiple format writers (Sections
 * 3/5) that reuse the same frozen object without ever recapturing or rereading
 * mutable preferences. {@link #serializeLegacyEntries()} and
 * {@link #writeLegacyEntries(AEFileOutputStream)} both emit one escaped, sorted,
 * deterministic {@code <entry key="..." value="..."/>} per line, in key order.
 * {@link #parseLegacyEntries(java.lang.Iterable)} decodes such lines back into a
 * snapshot so a recorded legacy header can be reopened and verified.
 */
public final class RecordingConfigurationSnapshot {

    private final List<SnapshotCodec.Entry> entries;

    private RecordingConfigurationSnapshot(List<SnapshotCodec.Entry> entries) {
        // Defensive unmodifiable copy; entries themselves are immutable value objects.
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Capture a snapshot from a chip, flushing live bias once if a biasgen exists.
     *
     * @param chip the chip whose configuration to snapshot; may be {@code null}
     * @return an immutable snapshot, never {@code null}
     */
    public static RecordingConfigurationSnapshot captureFromChip(AEChip chip) {
        if (chip == null) {
            return new RecordingConfigurationSnapshot(Collections.emptyList());
        }
        // One live bias flush: copy potArray/masterbias values into the node we are about to read.
        if (chip.getBiasgen() != null) {
            chip.getBiasgen().storePreferences();
        }
        Preferences prefs = chip.getPrefs();
        TreeMap<String, String> sorted = new TreeMap<>();
        if (prefs != null) {
            try {
                for (String k : prefs.keys()) {
                    sorted.put(k, prefs.get(k, ""));
                }
            } catch (java.util.prefs.BackingStoreException ex) {
                // Never fail recording because preferences could not be read; log and proceed with
                // whatever keys were collected. A controlled, non-fatal degradation is preferable to
                // aborting the header write for a snapshot that is best-effort metadata.
                java.util.logging.Logger.getLogger(RecordingConfigurationSnapshot.class.getName())
                        .warning("could not read all preference keys for snapshot: " + ex);
            }
        }
        List<SnapshotCodec.Entry> list = new ArrayList<>(sorted.size());
        for (java.util.Map.Entry<String, String> e : sorted.entrySet()) {
            list.add(new SnapshotCodec.Entry(e.getKey(), e.getValue()));
        }
        return new RecordingConfigurationSnapshot(list);
    }

    /**
     * Reconstruct an immutable snapshot from previously serialized legacy entry
     * lines (e.g. reopened from a recorded AEDAT header). Non-entry lines are
     * skipped; the surviving entries are kept sorted by key.
     *
     * @param lines header lines from a legacy AEDAT file
     * @return an immutable snapshot, never {@code null}
     */
    public static RecordingConfigurationSnapshot parseLegacyEntries(Iterable<String> lines) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (lines != null) {
            for (String line : lines) {
                SnapshotCodec.Entry e = SnapshotCodec.parseEntryLine(line);
                if (e != null) {
                    // Last occurrence wins, matching a key-sorted overwrite; keeps determinism.
                    sorted.put(e.getKey(), e.getValue());
                }
            }
        }
        List<SnapshotCodec.Entry> list = new ArrayList<>(sorted.size());
        for (java.util.Map.Entry<String, String> e : sorted.entrySet()) {
            list.add(new SnapshotCodec.Entry(e.getKey(), e.getValue()));
        }
        return new RecordingConfigurationSnapshot(list);
    }

    /**
     * @return whether this snapshot holds no entries (e.g. a null chip or empty prefs)
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * @return an immutable, key-sorted view of the captured entries
     */
    public List<SnapshotCodec.Entry> entries() {
        return entries;
    }

    /**
     * @param key the entry key
     * @return the captured value for {@code key}, or {@code null} if absent
     */
    public String get(String key) {
        for (SnapshotCodec.Entry e : entries) {
            if (e.getKey().equals(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Serialize the entries as one escaped, deterministic {@code <entry>} line
     * per entry, each terminated by {@code \n}, in sorted key order, with no
     * trailing newline. Byte-stable for a given snapshot and parseable by
     * {@link #parseLegacyEntries(java.lang.Iterable)}.
     *
     * @return the concatenated entry lines
     */
    public String serializeLegacyEntries() {
        StringBuilder sb = new StringBuilder(entries.size() * 80);
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            SnapshotCodec.Entry e = entries.get(i);
            sb.append("<entry key=\"")
                    .append(SnapshotCodec.escape(e.getKey()))
                    .append("\" value=\"")
                    .append(SnapshotCodec.escape(e.getValue()))
                    .append("\"/>");
        }
        return sb.toString();
    }

    /**
     * Append the escaped entries to the legacy header via the output stream's
     * line-oriented writer, each on its own physical line so line-oriented legacy
     * readers can recover them and a value can never inject a second line.
     *
     * @param os the legacy output stream to write to
     * @throws java.io.IOException if the write fails
     */
    public void writeLegacyEntries(AEFileOutputStream os) throws java.io.IOException {
        for (SnapshotCodec.Entry e : entries) {
            os.writeHeaderLine(String.format("<entry key=\"%s\" value=\"%s\"/>",
                    SnapshotCodec.escape(e.getKey()), SnapshotCodec.escape(e.getValue())));
        }
    }

    @Override
    public String toString() {
        return "RecordingConfigurationSnapshot{" + entries.size() + " entries}";
    }
}
