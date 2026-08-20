import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Writes a file to the JVM's real stdout (fd 1). Used by {@code ant help} so
 * Ant's DefaultLogger does not prefix every line with {@code [echo]}.
 * Must run with {@code <java fork="false"/>} in the Ant JVM.
 */
public final class PrintFileToStdout {

    private PrintFileToStdout() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: PrintFileToStdout <file>");
            System.exit(1);
        }
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        FileOutputStream out = new FileOutputStream(FileDescriptor.out);
        out.write(bytes);
        out.flush();
    }
}
