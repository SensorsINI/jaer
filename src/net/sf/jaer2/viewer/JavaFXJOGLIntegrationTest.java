package net.sf.jaer2.viewer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

import com.sun.javafx.perf.PerformanceTracker;

public class JavaFXJOGLIntegrationTest extends Application {
	private static final int RSIZE = 2;
	private static final int XLEN = 240;
	private static final int YLEN = 180;
	private static final int COLS = 2;
	private static final int ROWS = 1;

	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start() when ready.
		Application.launch(args);
	}

	@Override
	public void start(final Stage primaryStage) throws Exception {
		final StackPane root = new StackPane();

		final GridPane displayGrid = new GridPane();
		root.getChildren().add(displayGrid);

		displayGrid.setHgap(20);
		displayGrid.setVgap(20);

		for (int r = 0; r < JavaFXJOGLIntegrationTest.ROWS; r++) {
			for (int c = 0; c < JavaFXJOGLIntegrationTest.COLS; c++) {
				final JavaFXImgJOGLConnector fxJogl = new JavaFXImgJOGLConnector(JavaFXJOGLIntegrationTest.XLEN,
					JavaFXJOGLIntegrationTest.YLEN);
				displayGrid.add(fxJogl, c, r);

				fxJogl.setFitWidth((JavaFXJOGLIntegrationTest.XLEN * JavaFXJOGLIntegrationTest.RSIZE));
				fxJogl.setFitHeight((JavaFXJOGLIntegrationTest.YLEN * JavaFXJOGLIntegrationTest.RSIZE));

				fxJogl.addGLEventListener(new WriteRandom(((r + 1) * (c + 1)) % 4));

				final AnimationTimer animator = new AnimationTimer() {
					@Override
					public void handle(final long time) {
						fxJogl.getGLDrawable().display();
					}
				};
				/*
				 * final Animator animator = new Animator();
				 * animator.add(fxJogl.getGLDrawable());
				 * animator.setRunAsFastAsPossible(true);
				 */
				animator.start();
			}
		}

		// Write out the FPS on the screen.
		final VBox texts = new VBox();
		root.getChildren().add(texts);

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

		final Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		final Scene rootScene = new Scene(root, screen.getWidth(), screen.getHeight(), Color.GRAY);

		primaryStage.setTitle("jAER2 Viewer");
		primaryStage.setScene(rootScene);

		final PerformanceTracker perfTracker = PerformanceTracker.getSceneTracker(rootScene);

		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!Thread.currentThread().isInterrupted()) {
					try {
						Thread.sleep(1000);
					}
					catch (final InterruptedException e) {
						return;
					}

					final Runtime rt = Runtime.getRuntime();

					Platform.runLater(new Runnable() {
						@Override
						public void run() {
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
			}
		});

		primaryStage.show();
	}

	private class WriteRandom implements GLEventListener {
		private final BufferWorks buffer;

		public WriteRandom(final int c) {
			buffer = new BufferWorks(JavaFXJOGLIntegrationTest.XLEN, JavaFXJOGLIntegrationTest.YLEN,
				BUFFER_FORMATS.BYTE_NOALPHA, c);
		}

		@Override
		public void display(final GLAutoDrawable drawable) {
			buffer.update();

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
			gl.glOrthof(0, JavaFXJOGLIntegrationTest.XLEN, 0, JavaFXJOGLIntegrationTest.YLEN, -1, 1);

			gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
			gl.glLoadIdentity();

			gl.glViewport(0, 0, JavaFXJOGLIntegrationTest.XLEN, JavaFXJOGLIntegrationTest.YLEN);
		}

		@Override
		public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int arg3,
			final int arg4) {
		}

		private void render(final GLAutoDrawable drawable) {
			final GL2 gl = drawable.getGL().getGL2();

			gl.glClear(GL.GL_COLOR_BUFFER_BIT);

			gl.glDrawPixels(JavaFXJOGLIntegrationTest.XLEN, JavaFXJOGLIntegrationTest.YLEN, buffer.getGLColorFormat(),
				buffer.getGLFormat(), buffer.getBuffer());

			gl.glFlush();
		}
	}
}
