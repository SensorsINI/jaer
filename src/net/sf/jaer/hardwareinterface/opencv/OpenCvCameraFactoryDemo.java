package net.sf.jaer.hardwareinterface.opencv;

/**
 * Headless: OpenCV factory cache reads do not throw and do not require a camera.
 *
 * {@code java -cp "build/classes;jars/*;lib/*" net.sf.jaer.hardwareinterface.opencv.OpenCvCameraFactoryDemo}
 */
public final class OpenCvCameraFactoryDemo {

    private OpenCvCameraFactoryDemo() {
    }

    public static void main(String[] args) {
        OpenCvCameraFactory f = OpenCvCameraFactory.factory();
        f.setEnumerationEnabled(false);
        int n = f.getNumInterfacesAvailable();
        if (n < 0) {
            throw new AssertionError("cached count must be >= 0");
        }
        if (f.getInterface(-1) != null || f.getInterface(10_000) != null) {
            throw new AssertionError("out-of-range getInterface must be null");
        }
        String tip = OpenCvCameraFactory.tooltipHtml(0, 700, "DSHOW", 1280, 720, 30, "YUY2");
        if (!tip.contains("OpenCV camera index 0") || !tip.contains("DirectShow")
                || !tip.contains("1280") || !tip.contains("30 fps") || !tip.contains("YUY2")
                || !tip.contains("Zoom")) {
            throw new AssertionError("tooltipHtml missing expected webcam details: " + tip);
        }
        int mjpg = OpenCvCameraFactory.fourccCode("MJPG");
        if (!"MJPG".equals(OpenCvCameraFactory.fourccName(mjpg))) {
            throw new AssertionError("fourccCode/Name roundtrip MJPG");
        }
        if (OpenCvCameraFactory.fourccCode("YUY") != 0) {
            throw new AssertionError("short FOURCC must be 0");
        }
        f.probeNow();
        if (f.getNumInterfacesAvailable() != n) {
            throw new AssertionError("probeNow with enumeration disabled must not change cache");
        }
        System.out.println("OPENCV_CAMERA_FACTORY PASS n=" + n);
    }
}
