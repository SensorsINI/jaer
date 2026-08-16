package net.sf.jaer.eventio.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import eu.seebetter.ini.chips.davis.imu.IMUSample;

/**
 * Writes IMU samples as CSV:
 * {@code timestamp_us,ax_g,ay_g,az_g,gx_dps,gy_dps,gz_dps}.
 */
public final class ImuCsvSink implements AutoCloseable {

    private final PrintWriter out;
    private long samplesWritten;

    public ImuCsvSink(File file, File sourceFile) throws IOException {
        this.out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 1 << 16));
        out.println("# jAER IMU samples");
        out.println("# created " + new Date());
        out.println("# source-file: " + (sourceFile != null ? sourceFile : "(unknown)"));
        out.println("# imu-samples: One measurement per line: timestamp_us,ax_g,ay_g,az_g,gx_dps,gy_dps,gz_dps");
        out.println("timestamp_us,ax_g,ay_g,az_g,gx_dps,gy_dps,gz_dps");
    }

    public void write(IMUSample sample) {
        if (sample == null) {
            return;
        }
        out.printf("%d,%f,%f,%f,%f,%f,%f%n",
                sample.getTimestampUs(),
                sample.getAccelX(), sample.getAccelY(), sample.getAccelZ(),
                sample.getGyroTiltX(), sample.getGyroYawY(), sample.getGyroRollZ());
        samplesWritten++;
        if (out.checkError()) {
            throw new IllegalStateException("Error writing IMU CSV");
        }
    }

    public long getSamplesWritten() {
        return samplesWritten;
    }

    @Override
    public void close() {
        out.close();
    }
}
