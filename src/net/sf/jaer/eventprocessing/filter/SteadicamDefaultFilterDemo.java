package net.sf.jaer.eventprocessing.filter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Headless checks that Steadicam is a default filter just before Info on IMU
 * cameras (DAVIS, DVXplorer) and is not added for Prophesee / NRV.
 * Run after {@code ant compile}:
 * {@code java -cp "build/classes:lib/*" net.sf.jaer.eventprocessing.filter.SteadicamDefaultFilterDemo}
 */
public final class SteadicamDefaultFilterDemo {

    private static int assertions;

    private SteadicamDefaultFilterDemo() {
    }

    public static void main(String[] args) throws Exception {
        String aechip = Files.readString(Paths.get("src", "net", "sf", "jaer", "chip",
                "AEChip.java"), StandardCharsets.UTF_8);
        int steadicam = aechip.indexOf("addDefaultEventFilter(Steadicam.class)");
        int info = aechip.indexOf("addDefaultEventFilter(Info.class)");
        require(steadicam >= 0 && info > steadicam,
                "AEChip adds Steadicam immediately before Info");
        String gated = aechip.substring(aechip.indexOf("addDefaultEventFilter(HotPixelFilter.class)"), info);
        require(gated.contains("if (Steadicam.chipHasImu(this))"),
                "Steadicam default is gated on IMU (DAVIS / DVXplorer)");
        require(aechip.contains("Steadicam.ensurePresent(this)"),
                "saved filter lists get Steadicam inserted before Info");

        String steadicamSrc = Files.readString(Paths.get("src", "net", "sf", "jaer",
                "eventprocessing", "filter", "Steadicam.java"), StandardCharsets.UTF_8);
        require(steadicamSrc.contains("chip instanceof DavisChip || chip instanceof DVXplorer"),
                "chipHasImu is DAVIS or DVXplorer");
        require(!steadicamSrc.contains("Prophesee") || steadicamSrc.contains("Prophesee and"),
                "Steadicam documents Prophesee as no-IMU");
        require(steadicamSrc.contains("NRV cameras have no IMU"),
                "Steadicam documents NRV as no-IMU");
        require(steadicamSrc.contains("chain.get(i).getClass() == Info.class"),
                "ensurePresent inserts immediately before Info");

        String prophesee = Files.readString(Paths.get("src", "prophesee", "chip",
                "PropheseeIMX636HD.java"), StandardCharsets.UTF_8);
        require(!prophesee.contains("Steadicam"),
                "Prophesee chip does not add Steadicam");
        String nrv = Files.readString(Paths.get("src", "nrv", "chip",
                "NRVS5KRC1S.java"), StandardCharsets.UTF_8);
        require(!nrv.contains("Steadicam"),
                "NRV chip does not add Steadicam");

        System.out.println("STEADICAM_DEFAULT ASSERTIONS=" + assertions);
        System.out.println("STEADICAM_DEFAULT PASS");
    }

    private static void require(boolean cond, String msg) {
        assertions++;
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
