import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Writes a file to the JVM's real stdout (fd 1). Used by {@code ant help} so
 * Ant's DefaultLogger does not prefix every line with {@code [echo]}.
 * Must run in the Ant JVM via {@code taskdef}; do not use
 * {@code <java fork="false"/>} (that path calls {@code System.setSecurityManager},
 * which JDK 24+ rejects).
 */
public class PrintFileToStdout extends Task {

    private File file;

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public void execute() throws BuildException {
        if (file == null) {
            throw new BuildException("file is required", getLocation());
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileOutputStream out = new FileOutputStream(FileDescriptor.out);
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            throw new BuildException("Could not write " + file + " to stdout", e, getLocation());
        }
    }
}
