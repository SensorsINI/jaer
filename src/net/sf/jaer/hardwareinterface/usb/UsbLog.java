/*
 * UsbLog.java — thread tags for FINE USB hang debugging.
 */
package net.sf.jaer.hardwareinterface.usb;

import javax.swing.SwingUtilities;

/**
 * Short thread identity for {@code log.fine} around libusb. Temporary USB-hang
 * tracing; keep messages on logger {@code net.sf.jaer}.
 */
public final class UsbLog {

    private UsbLog() {
    }

    /** {@code thread=name id=N} plus {@code EDT} when on the event dispatch thread. */
    public static String t() {
        Thread th = Thread.currentThread();
        return String.format("thread=%s id=%d%s", th.getName(), th.threadId(),
                SwingUtilities.isEventDispatchThread() ? " EDT" : "");
    }

    /** Top frames of another thread (open/close workers stuck in native USB). */
    public static String stack(Thread th, int maxFrames) {
        if (th == null) {
            return "thread=null";
        }
        StackTraceElement[] st = th.getStackTrace();
        if (st == null || st.length == 0) {
            return th.getName() + " alive=" + th.isAlive() + " (no stack)";
        }
        StringBuilder sb = new StringBuilder(th.getName());
        sb.append(" alive=").append(th.isAlive()).append(" state=").append(th.getState());
        int n = Math.min(maxFrames, st.length);
        for (int i = 0; i < n; i++) {
            sb.append(" | ").append(st[i]);
        }
        return sb.toString();
    }
}
