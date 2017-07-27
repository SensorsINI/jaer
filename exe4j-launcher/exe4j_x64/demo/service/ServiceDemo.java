import java.io.*;
import java.util.Date;

public class ServiceDemo {

    public static void main(String[] args) {
        log("started at " + new Date());

        Thread currentThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new ShutdownThread(currentThread));

		// do your work until the thread is interrupted
		try {
			while (!currentThread.isInterrupted()) {
				Thread.sleep(2000);
				log("working at " + new Date());
			}
		} catch (InterruptedException e) {

		}

		// do clean up, shut down
		for (int i=0; i<3; i++) {
			try {
				Thread.sleep(1000);
				log("cleaning up at " + new Date());
			} catch (InterruptedException e) {

			}
		}

		// notify controller thread that we have finished
		synchronized (currentThread) {
			currentThread.notify();
		}

    }


    private static class ShutdownThread extends Thread {
        private Thread workerThread;

        public ShutdownThread(Thread workerThread) {
            this.workerThread = workerThread;
        }

        public void run() {
            log("shutdown requested at " + new Date());

            // request worker thread to finish
            workerThread.interrupt();

            // wait for the worker thread to finish
            try {
                synchronized (workerThread) {
                    workerThread.wait();
                }
            } catch (InterruptedException e) {
            }
            log("shutdown at " + new Date());
        }
    }


    private static void log(String val) {
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream("service_demo.log", true));
            pw.println(val);
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
