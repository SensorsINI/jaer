package net.sf.jaer.eventio;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

/**
 * OS-safe recording basenames from chip class + USB serial (single or muxed).
 */
public final class RecordingFilename {

    public static final int MAX_BASENAME = 180;
    public static final int MAX_SERIAL_ALNUM = 8;
    /** Short label in {@code Multidevice (N) …} names (3+ cameras). */
    public static final int MAX_DEVICE_ABBREV = 5;

    private RecordingFilename() {
    }

    /** One camera in a muxed filename. */
    public static final class DeviceToken {
        public final String chipSimpleName;
        public final String serialAlnum;

        public DeviceToken(String chipSimpleName, String serialAlnum) {
            this.chipSimpleName = chipSimpleName == null ? "jAER" : chipSimpleName;
            this.serialAlnum = serialAlnum == null ? "" : serialAlnum;
        }
    }

    /**
     * Letters and digits from a USB serial descriptor; last {@link #MAX_SERIAL_ALNUM}
     * chars. Empty if none.
     */
    public static String shortSerial(String serial) {
        if (serial == null || serial.isEmpty()) {
            return "";
        }
        StringBuilder alnum = new StringBuilder();
        for (int i = 0; i < serial.length(); i++) {
            char c = serial.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                alnum.append(c);
            }
        }
        if (alnum.length() <= MAX_SERIAL_ALNUM) {
            return alnum.toString();
        }
        return alnum.substring(alnum.length() - MAX_SERIAL_ALNUM);
    }

    /** USB string-descriptor serial, alnum only, or empty. */
    public static String usbSerialAlnum(AEChip chip) {
        if (chip == null || chip.getHardwareInterface() == null
                || !(chip.getHardwareInterface() instanceof USBInterface)) {
            return "";
        }
        USBInterface usb = (USBInterface) chip.getHardwareInterface();
        String[] desc = usb.getStringDescriptors();
        if (desc == null || desc.length < 3 || desc[2] == null) {
            return "";
        }
        return shortSerial(desc[2]);
    }

    /**
     * Replace anything other than {@code A-Za-z0-9._-} with {@code _}, collapse
     * repeats, strip trailing {@code .} and space (Windows).
     */
    public static String sanitizeSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "x";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        char last = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                if (c == '_' && last == '_') {
                    continue;
                }
                sb.append(c);
                last = c;
            } else {
                if (last != '_') {
                    sb.append('_');
                    last = '_';
                }
            }
        }
        while (sb.length() > 0) {
            char end = sb.charAt(sb.length() - 1);
            if (end == '.' || end == ' ' || end == '_') {
                sb.setLength(sb.length() - 1);
            } else {
                break;
            }
        }
        return sb.length() == 0 ? "x" : sb.toString();
    }

    public static String cameraToken(String chipSimpleName, String serialAlnum) {
        String chip = sanitizeSegment(chipSimpleName);
        String serial = serialAlnum == null ? "" : shortSerial(serialAlnum);
        if (serial.isEmpty()) {
            return chip;
        }
        return chip + "-" + sanitizeSegment(serial);
    }

    /**
     * At most {@link #MAX_DEVICE_ABBREV} chars for a muxed {@code Multidevice} name.
     * Known families: {@code NRV}, {@code Proph}, {@code Iniv}, {@code DVXm},
     * {@code DVXpl}, {@code DVS12}, {@code Davis}.
     */
    public static String deviceAbbrev(String chipSimpleName) {
        String raw = chipSimpleName == null || chipSimpleName.isEmpty() ? "jAER" : chipSimpleName;
        String n = raw.toLowerCase(Locale.ROOT);
        if (n.startsWith("nrv")) {
            return "NRV";
        }
        if (n.startsWith("prophesee")) {
            return "Proph";
        }
        if (n.startsWith("dvxplorermicro")) {
            return "DVXm";
        }
        if (n.startsWith("dvxplorer")) {
            return "DVXpl";
        }
        if (n.startsWith("dvs128")) {
            return "DVS12";
        }
        if (n.startsWith("davis")) {
            return "Davis";
        }
        if (n.startsWith("cochlea")) {
            return "Cochl";
        }
        if (n.startsWith("tmpdiff") || n.startsWith("dvs") || n.startsWith("retina")) {
            return "Iniv";
        }
        String san = sanitizeSegment(raw).replace("-", "").replace("_", "").replace(".", "");
        if (san.length() <= MAX_DEVICE_ABBREV) {
            return san;
        }
        return san.substring(0, MAX_DEVICE_ABBREV);
    }

    public static DeviceToken tokenFromChip(AEChip chip) {
        String name = chip == null ? "jAER" : chip.getClass().getSimpleName();
        return new DeviceToken(name, usbSerialAlnum(chip));
    }

    /**
     * Basename without extension. One or two cameras:
     * {@code token1_token2_datetime}. Three or more:
     * {@code Multidevice (N) Abb1 Abb2-datetime} (max
     * {@link #MAX_DEVICE_ABBREV} chars per device).
     */
    public static String muxedAedat4Base(List<DeviceToken> tokens, Date date) {
        Date when = date == null ? new Date() : date;
        String dateString = AEDataFile.DATE_FORMAT.format(when);
        List<DeviceToken> list = tokens == null ? new ArrayList<>() : tokens;
        if (list.isEmpty()) {
            return sanitizeSegment("jAER") + "_" + dateString;
        }
        if (list.size() > 2) {
            return multiDeviceBase(list, dateString);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            DeviceToken t = list.get(i);
            if (i > 0) {
                joined.append('_');
            }
            joined.append(cameraToken(t.chipSimpleName, t.serialAlnum));
        }
        return joined + "_" + dateString;
    }

    private static String multiDeviceBase(List<DeviceToken> list, String dateString) {
        String perCam = joinAbbrevs(list, false);
        String body = "Multidevice (" + list.size() + ") " + perCam + "-" + dateString;
        if (body.length() <= MAX_BASENAME) {
            return body;
        }
        String unique = joinAbbrevs(list, true);
        return "Multidevice (" + list.size() + ") " + unique + "-" + dateString;
    }

    private static String joinAbbrevs(List<DeviceToken> list, boolean unique) {
        StringBuilder sb = new StringBuilder();
        LinkedHashSet<String> seen = unique ? new LinkedHashSet<>() : null;
        for (DeviceToken t : list) {
            String abb = deviceAbbrev(t.chipSimpleName);
            if (seen != null && !seen.add(abb)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(abb);
        }
        return sb.length() == 0 ? "cams" : sb.toString();
    }

    public static String singleCameraBase(AEChip chip, Date date) {
        List<DeviceToken> one = new ArrayList<>(1);
        one.add(tokenFromChip(chip));
        return muxedAedat4Base(one, date);
    }

    /**
     * {@code folder/base-N.ext} that does not exist yet ({@code N} omitted first).
     */
    public static File uniqueFile(File folder, String base, String extension) {
        File dir = folder == null ? new File(".") : folder;
        String ext = extension == null ? AEDataFile.DATA_FILE_EXTENSION_AEDAT4 : extension;
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        File first = new File(dir, base + ext);
        if (!first.isFile()) {
            return first;
        }
        for (int suffix = 0; suffix <= 32; suffix++) {
            File f = new File(dir, base + "-" + suffix + ext);
            if (!f.isFile()) {
                return f;
            }
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }
}
