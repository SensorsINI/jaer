package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Hardware-free acceptance checks for the SciDVS host-only phase reset. */
public final class SciDVSPhaseResetCoordinatorDemo {

    private static int assertions;

    private SciDVSPhaseResetCoordinatorDemo() {
    }

    public static void main(final String[] args) throws Exception {
        final Class<?> coordinatorClass = requiredClass(
                "net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSPhaseResetCoordinator");
        final Class<?> hostClass = nestedClass(coordinatorClass, "Host");
        final Object coordinator = coordinatorClass.getDeclaredConstructor().newInstance();
        final Method execute = coordinatorClass.getDeclaredMethod("execute", hostClass);
        execute.setAccessible(true);

        exactSuccessOrder(coordinator, execute, hostClass);
        initiallyStoppedDvsIsRestored(coordinator, execute, hostClass);
        noOldReaderSkipsDrain(coordinator, execute, hostClass);
        everyFailureClosesBeforeUnsafeContinuation(coordinator, execute, hostClass);
        qualificationContract();

        System.out.println("SCIDVS_PHASE_RESET ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_PHASE_RESET PASS");
    }

    private static void exactSuccessOrder(final Object coordinator,
            final Method execute, final Class<?> hostClass) throws Exception {
        final FakeHost fake = new FakeHost(true, true, null);
        invoke(execute, coordinator, fake.proxy(hostClass));
        require(fake.operations.equals(List.of(
                "readDvsRun",
                "verifyOtherProducersStopped",
                "writeDvsRun:false",
                "hasActiveReader",
                "awaitSourceQuiescence",
                "writeUsbRun:false",
                "awaitWriterThreadZero",
                "stopReader",
                "clearEventEndpoint",
                "configureAcquisitionInfrastructure",
                "startFreshReader",
                "writeUsbRun:true",
                "armTimestampReset",
                "sendTimestampReset",
                "awaitTimestampReset",
                "disarmTimestampReset",
                "clearTimestampGuardForQualification",
                "beginQualification",
                "writeDvsRun:true",
                "awaitQualification",
                "commitQualification")),
                "successful correction follows the exact fail-closed source/USB/reader/reset/qualification order");
        require(!fake.operations.contains("failClosed"),
                "successful correction performs no cleanup path");
    }

    private static void initiallyStoppedDvsIsRestored(final Object coordinator,
            final Method execute, final Class<?> hostClass) throws Exception {
        final FakeHost fake = new FakeHost(false, true, null);
        invoke(execute, coordinator, fake.proxy(hostClass));
        final int qualify = fake.operations.indexOf("awaitQualification");
        final int restore = fake.operations.lastIndexOf("writeDvsRun:false");
        final int commit = fake.operations.indexOf("commitQualification");
        require(restore > qualify && commit > restore,
                "a source that was initially stopped is stopped again before publication commits");
    }

    private static void noOldReaderSkipsDrain(final Object coordinator,
            final Method execute, final Class<?> hostClass) throws Exception {
        final FakeHost fake = new FakeHost(true, false, null);
        invoke(execute, coordinator, fake.proxy(hostClass));
        require(!fake.operations.contains("awaitSourceQuiescence"),
                "initial startup without a reader does not wait on a nonexistent drain");
        require(fake.operations.indexOf("stopReader")
                < fake.operations.indexOf("clearEventEndpoint"),
                "reader terminality is still established before endpoint reset");
    }

    private static void everyFailureClosesBeforeUnsafeContinuation(
            final Object coordinator, final Method execute,
            final Class<?> hostClass) throws Exception {
        final List<String> throwingOperations = List.of(
                "readDvsRun", "verifyOtherProducersStopped", "writeDvsRun:false",
                "hasActiveReader", "awaitSourceQuiescence", "writeUsbRun:false",
                "awaitWriterThreadZero", "stopReader", "clearEventEndpoint",
                "configureAcquisitionInfrastructure", "startFreshReader",
                "writeUsbRun:true", "armTimestampReset", "sendTimestampReset",
                "awaitTimestampReset", "disarmTimestampReset",
                "clearTimestampGuardForQualification", "beginQualification",
                "writeDvsRun:true", "awaitQualification", "commitQualification");
        for (final String failure : throwingOperations) {
            final FakeHost fake = new FakeHost(true, true, failure);
            expectFailure(execute, coordinator, fake.proxy(hostClass), failure);
            require(fake.operations.contains("failClosed"),
                    failure + " invokes fail-closed cleanup");
            require(fake.operations.indexOf("failClosed") == fake.operations.size() - 1,
                    failure + " performs no operation after fail-closed cleanup");
            if (fake.operations.contains("clearEventEndpoint")) {
                require(fake.operations.indexOf("stopReader")
                        < fake.operations.indexOf("clearEventEndpoint"),
                        failure + " never clears the endpoint before reader terminality");
            }
            if (fake.operations.contains("startFreshReader")) {
                require(fake.operations.indexOf("clearEventEndpoint")
                        < fake.operations.indexOf("startFreshReader"),
                        failure + " never starts a replacement before endpoint reset");
            }
            if (fake.operations.contains("writeUsbRun:true")) {
                require(fake.operations.indexOf("startFreshReader")
                        < fake.operations.indexOf("writeUsbRun:true"),
                        failure + " never releases USB output before the fresh reader starts");
            }
        }

        for (final String falseResult : List.of(
                "stopReader:false", "awaitTimestampReset:false",
                "awaitQualification:false")) {
            final FakeHost fake = new FakeHost(true, true, falseResult);
            expectFailure(execute, coordinator, fake.proxy(hostClass), falseResult);
            require(fake.operations.get(fake.operations.size() - 1).equals("failClosed"),
                    falseResult + " fails closed");
            if (falseResult.equals("stopReader:false")) {
                require(!fake.operations.contains("clearEventEndpoint")
                        && !fake.operations.contains("startFreshReader"),
                        "unproven old-reader terminality forbids endpoint reset and replacement");
            }
            if (falseResult.equals("awaitTimestampReset:false")) {
                require(!fake.operations.contains("beginQualification"),
                        "missing owned reset marker forbids qualification");
            }
            if (falseResult.equals("awaitQualification:false")) {
                require(!fake.operations.contains("commitQualification"),
                        "insufficient stream qualification forbids publication");
            }
        }
    }

    private static void qualificationContract() throws Exception {
        final Class<?> type = requiredClass(
                "net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSPhaseQualification");
        final Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        final Method begin = method(type, "begin");
        final Method isQuarantining = method(type, "isQuarantining");
        final Method noteTransfer = method(type, "noteCompletedTransfer", int.class);
        final Method fail = method(type, "noteFailure", String.class);
        final Method await = method(type, "awaitSuccess", long.class);
        final Method commit = method(type, "commit");

        final Object valid = constructor.newInstance(32768, 3);
        invoke(begin, valid);
        require((Boolean) invoke(isQuarantining, valid),
                "qualification begins with publication quarantined");
        invoke(noteTransfer, valid, 10000);
        invoke(noteTransfer, valid, 10000);
        invoke(noteTransfer, valid, 12767);
        require(!(Boolean) invoke(await, valid, 1L),
                "three callbacks below 32768 bytes cannot qualify");
        invoke(noteTransfer, valid, 1);
        require((Boolean) invoke(await, valid, 10L),
                "at least 32768 bytes over at least three callbacks qualifies");
        invoke(commit, valid);
        require(!(Boolean) invoke(isQuarantining, valid),
                "only explicit commit opens publication");

        final Object tooFewCallbacks = constructor.newInstance(32768, 3);
        invoke(begin, tooFewCallbacks);
        invoke(noteTransfer, tooFewCallbacks, 20000);
        invoke(noteTransfer, tooFewCallbacks, 20000);
        require(!(Boolean) invoke(await, tooFewCallbacks, 1L),
                "byte threshold across only two callbacks cannot qualify");

        final Object failed = constructor.newInstance(32768, 3);
        invoke(begin, failed);
        invoke(noteTransfer, failed, 32768);
        invoke(noteTransfer, failed, 1);
        invoke(noteTransfer, failed, 1);
        invoke(fail, failed, "additional timestamp reset marker");
        require(!(Boolean) invoke(await, failed, 1L),
                "a transfer/reset failure permanently prevents qualification");
        expectIllegalState(() -> invoke(commit, failed),
                "failed qualification cannot commit publication");
    }

    private static void expectFailure(final Method execute,
            final Object coordinator, final Object host, final String description)
            throws Exception {
        try {
            invoke(execute, coordinator, host);
            throw new AssertionError(description + " should fail");
        } catch (final IOException | IllegalStateException expected) {
            require(true, description + " propagates its original failure");
        }
    }

    private static void expectIllegalState(final CheckedAction action,
            final String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (final IllegalStateException expected) {
            require(true, description);
        }
    }

    private static Class<?> requiredClass(final String name) {
        try {
            return Class.forName(name);
        } catch (final ClassNotFoundException expected) {
            throw new AssertionError("missing phase-reset production class " + name, expected);
        }
    }

    private static Class<?> nestedClass(final Class<?> owner, final String name) {
        return Arrays.stream(owner.getDeclaredClasses())
                .filter(item -> item.getSimpleName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing phase-reset contract " + owner.getSimpleName() + "." + name));
    }

    private static Method method(final Class<?> owner, final String name,
            final Class<?>... parameters) throws Exception {
        final Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(final Method method, final Object target,
            final Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (final InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw wrapped;
        }
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class FakeHost implements InvocationHandler {
        private final boolean priorDvsRun;
        private final boolean activeReader;
        private final String failure;
        private final List<String> operations = new ArrayList<>();

        FakeHost(final boolean priorDvsRun, final boolean activeReader,
                final String failure) {
            this.priorDvsRun = priorDvsRun;
            this.activeReader = activeReader;
            this.failure = failure;
        }

        Object proxy(final Class<?> hostClass) {
            return Proxy.newProxyInstance(hostClass.getClassLoader(),
                    new Class<?>[]{hostClass}, this);
        }

        @Override
        public Object invoke(final Object proxy, final Method method,
                final Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            final String operation = operation(method, args);
            operations.add(operation);
            if (operation.equals(failure)) {
                throw new IOException("injected " + failure);
            }
            if (method.getName().equals("failClosed")) {
                return null;
            }
            if (method.getName().equals("readDvsRun")) {
                return priorDvsRun;
            }
            if (method.getName().equals("hasActiveReader")) {
                return activeReader;
            }
            if (method.getName().equals("stopReader")) {
                return !"stopReader:false".equals(failure);
            }
            if (method.getName().equals("awaitTimestampReset")) {
                return !"awaitTimestampReset:false".equals(failure);
            }
            if (method.getName().equals("awaitQualification")) {
                return !"awaitQualification:false".equals(failure);
            }
            return null;
        }

        private static String operation(final Method method, final Object[] args) {
            if ((method.getName().equals("writeDvsRun")
                    || method.getName().equals("writeUsbRun"))
                    && args != null && args.length == 1) {
                return method.getName() + ":" + args[0];
            }
            return method.getName();
        }
    }
}
