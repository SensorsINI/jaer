package net.sf.jaer.graphics;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Headless behavior coverage for AEViewer live-route ownership and typed decline. */
public final class AEViewerRouteStateDemo {

    private static int assertions;

    private AEViewerRouteStateDemo() {
    }

    public static void main(final String[] args) throws Exception {
        final Class<?> stateClass = requiredClass();
        final Constructor<?> constructor = stateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object state = constructor.newInstance();
        final Method select = accessible(stateClass.getDeclaredMethod(
                "selectLiveRoute", Object.class, long.class, boolean.class));
        final Method decline = accessible(stateClass.getDeclaredMethod(
                "typedDeliveryDeclined", Object.class, long.class));

        final FakeMonitor monitor = new FakeMonitor();
        final Object firstTyped = invoke(select, state, monitor, 1L, true);
        require(routeName(firstTyped).equals("AUTHORITATIVE_TYPED"),
                "first eligible iteration requests authoritative typed delivery");
        applyRestart(firstTyped, monitor);
        monitor.acquisitionEnabled = true;

        final Object declineToLegacy = invoke(decline, state, monitor, 1L);
        require(routeName(declineToLegacy).equals("LEGACY_RAW"),
                "null typed delivery selects legacy raw");
        require(restartRequired(declineToLegacy),
                "typed decline requires one stop before legacy acquisition");
        applyRestart(declineToLegacy, monitor);
        require(monitor.stopCount == 1,
                "typed decline stops the active typed session exactly once");

        monitor.acquisitionEnabled = true; // grabInput started the legacy reader
        monitor.legacyPolls++;
        final Object secondIteration = invoke(select, state, monitor, 1L, true);
        require(routeName(secondIteration).equals("LEGACY_RAW"),
                "second iteration stays on legacy after typed decline");
        require(!restartRequired(secondIteration),
                "second legacy iteration does not stop/restart acquisition");
        applyRestart(secondIteration, monitor);
        monitor.legacyPolls++;
        require(monitor.stopCount == 1 && monitor.legacyPolls == 2,
                "two legacy iterations run without stop/restart oscillation");

        final Object newSession = invoke(select, state, monitor, 2L, true);
        require(routeName(newSession).equals("AUTHORITATIVE_TYPED"),
                "new session clears the cached typed decline");
        require(restartRequired(newSession),
                "new session applies its authoritative route once");

        final FakeMonitor replacement = new FakeMonitor();
        final Object newMonitor = invoke(select, state, replacement, 2L, true);
        require(routeName(newMonitor).equals("AUTHORITATIVE_TYPED"),
                "monitor replacement clears the cached typed decline");
        require(restartRequired(newMonitor),
                "monitor replacement applies its authoritative route once");

        System.out.println("AEVIEWER_ROUTE_STATE ASSERTIONS=" + assertions);
        System.out.println("AEVIEWER_ROUTE_STATE PASS");
    }

    private static Class<?> requiredClass() {
        try {
            return Class.forName("net.sf.jaer.graphics.AEViewerRouteState");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("AEViewerRouteState helper missing", missing);
        }
    }

    private static Method accessible(final Method method) {
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(final Method method, final Object target,
            final Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static String routeName(final Object decision) throws Exception {
        final Method route = accessible(decision.getClass().getDeclaredMethod("getRoute"));
        return String.valueOf(invoke(route, decision));
    }

    private static boolean restartRequired(final Object decision) throws Exception {
        final Method restart = accessible(decision.getClass().getDeclaredMethod(
                "requiresAcquisitionRestart"));
        return (Boolean) invoke(restart, decision);
    }

    private static void applyRestart(final Object decision, final FakeMonitor monitor)
            throws Exception {
        if (restartRequired(decision) && monitor.acquisitionEnabled) {
            monitor.acquisitionEnabled = false;
            monitor.stopCount++;
        }
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static final class FakeMonitor {
        private boolean acquisitionEnabled;
        private int stopCount;
        private int legacyPolls;
    }
}
