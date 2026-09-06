import java.util.Locale;

/**
 * Minutes for jaer-sample-data.zip at 10 MB/s Wi-Fi. Prints one line for Ant
 * {@code outputproperty} / install4j {@code jaer.sampleDataEta}.
 */
public final class SampleDataEta {

    public static void main(String[] args) {
        int zipMiB = 0;
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            try {
                zipMiB = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        System.out.print(etaMinutesAt10MBps(zipMiB));
    }

    static String etaMinutesAt10MBps(int zipMiB) {
        if (zipMiB <= 0) {
            return "time unknown (missing SIZE.txt)";
        }
        double minutes = zipMiB / 10.0 / 60.0;
        if (minutes < 0.1) {
            minutes = 0.1;
        }
        return String.format(Locale.ROOT, "about %.1f min at 10 MB/s Wi-Fi", minutes);
    }
}
