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
        f.probeNow();
        if (f.getNumInterfacesAvailable() != n) {
            throw new AssertionError("probeNow with enumeration disabled must not change cache");
        }
        System.out.println("OPENCV_CAMERA_FACTORY PASS n=" + n);
    }
}
