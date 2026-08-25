package net.sf.jaer.eventio.opencv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Process-level tests for the bounded {@code v4l2-ctl} fallback runner. */
public class V4l2LoopbackSinkProcessTest {

    private static final String CHILD_ARGUMENT = "--v4l2-runner-child";
    private static final String FIXTURE_DEVICE = "fixture-device-not-dev-video";
    private static final long RUNNER_TIMEOUT_MILLIS = 300L;
    private static final long CHILD_TIMEOUT_MILLIS = 4_000L;
    private static final long PROCESS_DEATH_TIMEOUT_MILLIS = 3_000L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(V4l2LoopbackSinkProcessTest.class);
    }

    @Test(timeout = 15_000L)
    public void floodingStdoutAndStderrTimesOutWithoutDeadlockAndKillsDescendants()
            throws Exception {
        Fixture fixture = createFixture("flood", floodingScript());
        try {
            ChildResult result = runFixtureChild(fixture, "flood");

            assertFalse("production runner deadlocked on full stdout/stderr pipes; child output:\n"
                    + result.output, result.timedOut);
            assertEquals("fixture child exit; output:\n" + result.output, 0, result.exitCode);
            assertContextualIOException(result.output, "timed out", "640x480");
            assertFixtureProcessesDead(fixture.pidFile, 2);
        } finally {
            forceFixtureProcessesDead(fixture.pidFile);
        }
    }

    @Test(timeout = 15_000L)
    public void nonzeroExitYieldsContextualIOException() throws Exception {
        Fixture fixture = createFixture("nonzero", nonzeroScript());
        try {
            ChildResult result = runFixtureChild(fixture, "nonzero");

            assertFalse("nonzero fixture child hung; output:\n" + result.output,
                    result.timedOut);
            assertEquals("fixture child exit; output:\n" + result.output, 0, result.exitCode);
            assertContextualIOException(result.output, "exited 7", "320x240");
            assertFixtureProcessesDead(fixture.pidFile, 1);
        } finally {
            forceFixtureProcessesDead(fixture.pidFile);
        }
    }

    @Test(timeout = 15_000L)
    public void interruptionYieldsContextualIOExceptionRestoresInterruptAndKillsDescendants()
            throws Exception {
        Fixture fixture = createFixture("interrupt", sleepingScript());
        try {
            ChildResult result = runFixtureChild(fixture, "interrupt");

            assertFalse("interrupted runner did not return within the bound; output:\n"
                    + result.output, result.timedOut);
            assertEquals("fixture child exit; output:\n" + result.output, 0, result.exitCode);
            assertContextualIOException(result.output, "interrupted", "800x600");
            assertTrue("runner thread interrupt was not restored; output:\n" + result.output,
                    result.output.contains("RESULT_INTERRUPTED=true"));
            assertFixtureProcessesDead(fixture.pidFile, 2);
        } finally {
            forceFixtureProcessesDead(fixture.pidFile);
        }
    }

    @Test(timeout = 15_000L)
    public void zeroExitPasses() throws Exception {
        Fixture fixture = createFixture("zero", zeroScript());
        try {
            ChildResult result = runFixtureChild(fixture, "zero");

            assertFalse("zero-exit fixture child hung; output:\n" + result.output,
                    result.timedOut);
            assertEquals("fixture child exit; output:\n" + result.output, 0, result.exitCode);
            assertTrue("zero exit did not pass; output:\n" + result.output,
                    result.output.contains("RESULT_OK"));
            assertFalse("zero exit unexpectedly threw; output:\n" + result.output,
                    result.output.contains("RESULT_IO="));
            assertFixtureProcessesDead(fixture.pidFile, 1);
        } finally {
            forceFixtureProcessesDead(fixture.pidFile);
        }
    }

    @Test
    public void commandAndTimeoutSeamIsPackagePrivate() throws Exception {
        Constructor<V4l2LoopbackSink> constructor = V4l2LoopbackSink.class
                .getDeclaredConstructor(String.class, List.class, long.class);
        int modifiers = constructor.getModifiers();
        assertFalse("injectable constructor must not be public", Modifier.isPublic(modifiers));
        assertFalse("injectable constructor must not be protected", Modifier.isProtected(modifiers));
        assertFalse("injectable constructor must not be private", Modifier.isPrivate(modifiers));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !CHILD_ARGUMENT.equals(args[0])) {
            throw new IllegalArgumentException("expected child mode, fixture path, and PID file");
        }
        runProductionRunner(args[1], Path.of(args[2]));
    }

    private static void runProductionRunner(String fixtureCommand, Path pidFile)
            throws Exception {
        String mode = System.getenv("V4L2_FIXTURE_MODE");
        int width;
        int height;
        switch (mode) {
            case "flood":
                width = 640;
                height = 480;
                break;
            case "nonzero":
            case "zero":
                width = 320;
                height = 240;
                break;
            case "interrupt":
                width = 800;
                height = 600;
                interruptCurrentThreadWhenFixtureStarts(pidFile);
                break;
            default:
                throw new IllegalArgumentException("unknown fixture mode " + mode);
        }

        V4l2LoopbackSink sink = constructSink(fixtureCommand);
        Method setFormat = V4l2LoopbackSink.class
                .getDeclaredMethod("setFmtWithV4l2Ctl", int.class, int.class);
        setFormat.setAccessible(true);
        try {
            setFormat.invoke(sink, width, height);
            System.out.println("RESULT_OK");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof IOException)) {
                cause.printStackTrace(System.out);
                System.out.println("RESULT_UNEXPECTED=" + cause);
                System.exit(3);
            }
            System.out.println("RESULT_IO=" + oneLine(cause.getMessage()));
            System.out.println("RESULT_INTERRUPTED=" + Thread.currentThread().isInterrupted());
        }
    }

    private static V4l2LoopbackSink constructSink(String fixtureCommand) throws Exception {
        try {
            Constructor<V4l2LoopbackSink> constructor = V4l2LoopbackSink.class
                    .getDeclaredConstructor(String.class, List.class, long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(FIXTURE_DEVICE, List.of(fixtureCommand),
                    RUNNER_TIMEOUT_MILLIS);
        } catch (NoSuchMethodException absentBeforeRepair) {
            // RED path: the unmodified runner resolves the temporary v4l2-ctl via PATH.
            return new V4l2LoopbackSink(FIXTURE_DEVICE);
        }
    }

    private static void interruptCurrentThreadWhenFixtureStarts(Path pidFile) {
        Thread target = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (!Files.exists(pidFile) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            target.interrupt();
        }, "v4l2-fixture-interrupter");
        interrupter.setDaemon(true);
        interrupter.start();
    }

    private Fixture createFixture(String label, String scriptBody) throws IOException {
        Path directory = temporaryFolder.newFolder("v4l2-fixture-" + label).toPath();
        Path command = directory.resolve("v4l2-ctl");
        Path pidFile = directory.resolve("pids.txt");
        Files.writeString(command, scriptBody, StandardCharsets.UTF_8);
        assertTrue("could not make fixture executable: " + command,
                command.toFile().setExecutable(true, false));
        return new Fixture(command, pidFile);
    }

    private ChildResult runFixtureChild(Fixture fixture, String mode) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = List.of(java.toString(), "-cp",
                System.getProperty("java.class.path"),
                V4l2LoopbackSinkProcessTest.class.getName(), CHILD_ARGUMENT,
                fixture.command.toString(), fixture.pidFile.toString());
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("V4L2_FIXTURE_MODE", mode);
        builder.environment().put("V4L2_FIXTURE_PID_FILE", fixture.pidFile.toString());
        builder.environment().put("PATH", fixture.command.getParent() + ":"
                + builder.environment().getOrDefault("PATH", ""));
        Process child = builder.start();
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        Thread outputReader = new Thread(() -> {
            try {
                child.getInputStream().transferTo(outputBytes);
            } catch (IOException closedDuringForcedCleanup) {
                // Expected only when the RED-path harness must kill a stuck child.
            }
        }, "v4l2-fixture-child-output");
        outputReader.start();
        boolean exited = child.waitFor(CHILD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (!exited) {
            destroyTreeForTest(child);
        }
        outputReader.join(PROCESS_DEATH_TIMEOUT_MILLIS);
        String output = outputBytes.toString(StandardCharsets.UTF_8);
        return new ChildResult(!exited, exited ? child.exitValue() : Integer.MIN_VALUE, output);
    }

    private static void assertContextualIOException(String output, String disposition,
            String dimensions) {
        assertTrue("expected IOException result; output:\n" + output,
                output.contains("RESULT_IO="));
        assertTrue("missing disposition '" + disposition + "'; output:\n" + output,
                output.toLowerCase().contains(disposition.toLowerCase()));
        assertTrue("missing v4l2-ctl command context; output:\n" + output,
                output.contains("v4l2-ctl"));
        assertTrue("missing device context; output:\n" + output,
                output.contains(FIXTURE_DEVICE));
        assertTrue("missing format context " + dimensions + "; output:\n" + output,
                output.contains(dimensions));
    }

    private static void assertFixtureProcessesDead(Path pidFile, int minimumPidCount)
            throws Exception {
        List<Long> pids = readPids(pidFile);
        assertTrue("fixture did not record the expected process tree in " + pidFile
                + "; pids=" + pids, pids.size() >= minimumPidCount);
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(PROCESS_DEATH_TIMEOUT_MILLIS);
        while (anyAlive(pids) && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        assertFalse("fixture process remained alive after production runner returned; pids="
                + pids, anyAlive(pids));
    }

    private static void forceFixtureProcessesDead(Path pidFile) throws Exception {
        List<Long> pids = readPids(pidFile);
        for (long pid : pids) {
            ProcessHandle.of(pid).ifPresent(handle -> {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(PROCESS_DEATH_TIMEOUT_MILLIS);
        while (anyAlive(pids) && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static List<Long> readPids(Path pidFile) throws IOException {
        if (!Files.exists(pidFile)) {
            return List.of();
        }
        List<Long> pids = new ArrayList<>();
        for (String line : Files.readAllLines(pidFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                pids.add(Long.valueOf(line.trim()));
            }
        }
        return pids;
    }

    private static boolean anyAlive(List<Long> pids) {
        for (long pid : pids) {
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static void destroyTreeForTest(Process process) throws InterruptedException {
        List<ProcessHandle> descendants = new ArrayList<>(
                process.toHandle().descendants().toList());
        Collections.reverse(descendants);
        for (ProcessHandle descendant : descendants) {
            if (descendant.isAlive()) {
                descendant.destroyForcibly();
            }
        }
        process.destroyForcibly();
        process.waitFor(PROCESS_DEATH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static String oneLine(String value) {
        return value == null ? "null" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String floodingScript() {
        return "#!/bin/sh\n"
                + "trap '' TERM\n"
                + "sleep 60 &\n"
                + "child=$!\n"
                + "printf '%s\\n%s\\n' \"$$\" \"$child\" > \"$V4L2_FIXTURE_PID_FILE\"\n"
                + "i=0\n"
                + "while [ \"$i\" -lt 20000 ]; do\n"
                + "  printf '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\\n'\n"
                + "  printf 'fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210\\n' >&2\n"
                + "  i=$((i + 1))\n"
                + "done\n"
                + "sleep 60\n";
    }

    private static String sleepingScript() {
        return "#!/bin/sh\n"
                + "trap '' TERM\n"
                + "sleep 60 &\n"
                + "child=$!\n"
                + "printf '%s\\n%s\\n' \"$$\" \"$child\" > \"$V4L2_FIXTURE_PID_FILE\"\n"
                + "wait \"$child\"\n";
    }

    private static String nonzeroScript() {
        return "#!/bin/sh\n"
                + "printf '%s\\n' \"$$\" > \"$V4L2_FIXTURE_PID_FILE\"\n"
                + "printf 'intentional v4l2 fixture failure\\n' >&2\n"
                + "exit 7\n";
    }

    private static String zeroScript() {
        return "#!/bin/sh\n"
                + "printf '%s\\n' \"$$\" > \"$V4L2_FIXTURE_PID_FILE\"\n"
                + "exit 0\n";
    }

    private static final class Fixture {
        final Path command;
        final Path pidFile;

        Fixture(Path command, Path pidFile) {
            this.command = command;
            this.pidFile = pidFile;
        }
    }

    private static final class ChildResult {
        final boolean timedOut;
        final int exitCode;
        final String output;

        ChildResult(boolean timedOut, int exitCode, String output) {
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
