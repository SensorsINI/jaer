package net.sf.jaer2.viewer;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLCapabilitiesImmutable;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import jogamp.opengl.GLGraphicsConfigurationUtil;
import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.Animator;
import com.sun.javafx.perf.PerformanceTracker;

public class jAER2joglpxfxcanvas extends Application implements GLEventListener {
	private static long FPS = 0;
	private static long FPS_FX = 0;
	private static final int RSIZE = 2;
	private static final int XLEN = 640;
	private static final int YLEN = 480;

	private static final BufferWorks buffer = new BufferWorks(jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN,
		BUFFER_FORMATS.BYTE_NOALPHA, 0);

	private static final int imageBufferRGB8Number = 2;
	private static final Semaphore syncImageBufferRGB8Swap[] = new Semaphore[jAER2joglpxfxcanvas.imageBufferRGB8Number];
	private static final ByteBuffer imageBufferRGB8[] = new ByteBuffer[jAER2joglpxfxcanvas.imageBufferRGB8Number];
	private static final PixelFormat<ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();
	private final GraphicsContext gc;
	private final PixelWriter pxWriter;

	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start() when ready.
		Application.launch(args);
	}

	public jAER2joglpxfxcanvas() {
		gc = null;
		pxWriter = null;
	}

	public jAER2joglpxfxcanvas(final GraphicsContext gctx) {
		gc = gctx;
		pxWriter = gc.getPixelWriter();

		// Init image buffers when drawing.
		for (int i = 0; i < jAER2joglpxfxcanvas.imageBufferRGB8Number; i++) {
			jAER2joglpxfxcanvas.syncImageBufferRGB8Swap[i] = new Semaphore(1);
			jAER2joglpxfxcanvas.imageBufferRGB8[i] = Buffers.newDirectByteBuffer(4 * jAER2joglpxfxcanvas.XLEN
				* jAER2joglpxfxcanvas.YLEN);
		}
	}

	@Override
	public void start(final Stage primaryStage) {
		GLProfile.initSingleton();
		final GLProfile glp = GLProfile.get(GLProfile.GL2);

		final GLCapabilities caps = new GLCapabilities(glp);
		caps.setOnscreen(false);
		caps.setHardwareAccelerated(true);
		caps.setFBO(true);

		System.out.println("Requested GL Caps: " + caps);
		final GLDrawableFactory factory = GLDrawableFactory.getFactory(caps.getGLProfile());
		final GLCapabilitiesImmutable expCaps = GLGraphicsConfigurationUtil.fixGLCapabilities(caps, factory, null);
		System.out.println("Expected  GL Caps: " + expCaps);

		final GLOffscreenAutoDrawable glDrawBuffer = factory.createOffscreenAutoDrawable(null, caps, null,
			jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN, null);
		glDrawBuffer.setAutoSwapBufferMode(true);

		glDrawBuffer.display();

		final Animator animator = new Animator();
		animator.add(glDrawBuffer);
		animator.setRunAsFastAsPossible(true);
		animator.start();

		final StackPane root = new StackPane();

		final Canvas canvas = new Canvas(jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN);
		final GraphicsContext gctx = canvas.getGraphicsContext2D();
		root.getChildren().add(canvas);

		canvas.setScaleX(jAER2joglpxfxcanvas.RSIZE);
		canvas.setScaleY(jAER2joglpxfxcanvas.RSIZE);

		glDrawBuffer.addGLEventListener(new jAER2joglpxfxcanvas(gctx));

		// Write out the FPS on the screen.
		final VBox texts = new VBox();
		root.getChildren().add(texts);

		final Text fpsTxt = new Text();
		texts.getChildren().add(fpsTxt);

		fpsTxt.setFill(Color.WHITE);
		fpsTxt.setFont(new Font(36));

		final Text fpsFXTxt = new Text();
		texts.getChildren().add(fpsFXTxt);

		fpsFXTxt.setFill(Color.WHITE);
		fpsFXTxt.setFont(new Font(36));

		final Text fpsFXpftTxt = new Text();
		texts.getChildren().add(fpsFXpftTxt);

		fpsFXpftTxt.setFill(Color.WHITE);
		fpsFXpftTxt.setFont(new Font(36));

		final Text usedMemTxt = new Text();
		texts.getChildren().add(usedMemTxt);

		usedMemTxt.setFill(Color.WHITE);
		usedMemTxt.setFont(new Font(36));

		final Text freeMemTxt = new Text();
		texts.getChildren().add(freeMemTxt);

		freeMemTxt.setFill(Color.WHITE);
		freeMemTxt.setFont(new Font(36));

		final Text totMemTxt = new Text();
		texts.getChildren().add(totMemTxt);

		totMemTxt.setFill(Color.WHITE);
		totMemTxt.setFont(new Font(36));

		final Text maxMemTxt = new Text();
		texts.getChildren().add(maxMemTxt);

		maxMemTxt.setFill(Color.WHITE);
		maxMemTxt.setFont(new Font(36));

		final Scene rootScene = new Scene(root, 1920, 1080, Color.BLACK);

		primaryStage.setTitle("jAER2 Viewer");
		primaryStage.setScene(rootScene);

		final PerformanceTracker perfTracker = PerformanceTracker.getSceneTracker(rootScene);

		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				final long start = System.currentTimeMillis();

				while (!Thread.currentThread().isInterrupted()) {
					try {
						Thread.sleep(1000);
					}
					catch (final InterruptedException e) {
						return;
					}

					final long fpsPrint = jAER2joglpxfxcanvas.FPS / ((System.currentTimeMillis() - start) / 1000);

					final long fpsFXPrint = jAER2joglpxfxcanvas.FPS_FX / ((System.currentTimeMillis() - start) / 1000);

					final Runtime rt = Runtime.getRuntime();

					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							fpsTxt.setText("JOGL FPS: " + fpsPrint);

							fpsFXTxt.setText("JOGL -> JavaFX FPS: " + fpsFXPrint);

							fpsFXpftTxt.setText(String.format(
								"JavaFX (perfTracker):\n\tavgFPS %d, instaFPS %d\n\tavgPulse %d, instaPulse %d",
								(int) perfTracker.getAverageFPS(), (int) perfTracker.getInstantFPS(),
								(int) perfTracker.getAveragePulses(), (int) perfTracker.getInstantPulses()));

							/* Current amount of memory the JVM is using */
							usedMemTxt.setText("Used memory (bytes): " + (rt.totalMemory() - rt.freeMemory()));

							/* Total amount of free memory available to the JVM */
							freeMemTxt.setText("Free memory (bytes): " + rt.freeMemory());

							/* Total memory currently in use by the JVM */
							totMemTxt.setText("Total memory (bytes): " + rt.totalMemory());

							/* Maximum amount of memory the JVM will attempt to use */
							maxMemTxt.setText("Maximum memory (bytes): " + rt.maxMemory());
						}
					});
				}
			}
		});
		t.start();

		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(final WindowEvent event) {
				t.interrupt();
				animator.getThread().interrupt();
				animator.stop();
				glDrawBuffer.destroy();
			}
		});

		primaryStage.show();
	}

	@Override
	public void display(final GLAutoDrawable drawable) {
		jAER2joglpxfxcanvas.FPS++;

		jAER2joglpxfxcanvas.buffer.update();

		render(drawable);
	}

	@Override
	public void dispose(final GLAutoDrawable drawable) {
	}

	@Override
	public void init(final GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();

		gl.setSwapInterval(0);

		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrthof(0, 1920, 0, 1080, -1, 1);

		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glLoadIdentity();

		gl.glViewport(0, 0, 1920, 1080);
	}

	@Override
	public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int arg3, final int arg4) {
	}

	private void render(final GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT);

		gl.glDrawPixels(jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN,
			jAER2joglpxfxcanvas.buffer.getGLColorFormat(), jAER2joglpxfxcanvas.buffer.getGLFormat(),
			jAER2joglpxfxcanvas.buffer.getBuffer());

		gl.glFlush();

		// Read back final result.
		int selectedImageBuffer = 0;

		while (!jAER2joglpxfxcanvas.syncImageBufferRGB8Swap[selectedImageBuffer].tryAcquire()) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}

			selectedImageBuffer = (selectedImageBuffer + 1) % jAER2joglpxfxcanvas.imageBufferRGB8Number;
		}

		gl.glReadBuffer(GL.GL_FRONT);
		gl.glReadPixels(0, 0, jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
			jAER2joglpxfxcanvas.imageBufferRGB8[selectedImageBuffer]);

		final int releaseSelectedImageBuffer = selectedImageBuffer;

		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				// Write current buffer out.
				pxWriter.setPixels(0, 0, jAER2joglpxfxcanvas.XLEN, jAER2joglpxfxcanvas.YLEN,
					jAER2joglpxfxcanvas.pxFormat, jAER2joglpxfxcanvas.imageBufferRGB8[releaseSelectedImageBuffer],
					jAER2joglpxfxcanvas.XLEN * 4);

				jAER2joglpxfxcanvas.syncImageBufferRGB8Swap[releaseSelectedImageBuffer].release();

				jAER2joglpxfxcanvas.FPS_FX++;
			}
		});
	}
}
