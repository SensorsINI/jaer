package net.sf.jaer2.viewer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

public class jAER2fx extends Application {
	private static long FPS = 0;
	private static final int RSIZE = 2;
	private static final int XLEN = 640;
	private static final int YLEN = 480;

	private static final BufferWorks buffer = new BufferWorks(jAER2fx.XLEN, jAER2fx.YLEN, BUFFER_FORMATS.BYTE_NOALPHA,
		0);

	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start()
		// when ready.
		Application.launch(args);
	}

	@Override
	public void start(final Stage primaryStage) {
		final StackPane root = new StackPane();

		final Group rectangles = new Group();
		final Rectangle[] rects = new Rectangle[jAER2fx.YLEN * jAER2fx.XLEN];

		// Create all rectangles representing camera pixels.
		for (int y = 0, point = 0; y < jAER2fx.YLEN; y++) {
			for (int x = 0; x < jAER2fx.XLEN; x++, point++) {
				rects[point] = new Rectangle(x, y, 1, 1);
				rectangles.getChildren().add(rects[point]);
			}
		}

		root.getChildren().add(rectangles);

		rectangles.setScaleX(jAER2fx.RSIZE);
		rectangles.setScaleY(jAER2fx.RSIZE);

		final AnimationTimer animator = new AnimationTimer() {
			@Override
			public void handle(final long time) {
				jAER2fx.FPS++;

				jAER2fx.buffer.update();

				render(rects);
			}
		};
		animator.start();

		final Scene rootScene = new Scene(root, 1920, 1080, Color.BLACK);

		primaryStage.setTitle("jAER2 Viewer");
		primaryStage.setScene(rootScene);

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

					final long fpsPrint = jAER2fx.FPS / ((System.currentTimeMillis() - start) / 1000);
					System.out.println("FPS are: " + fpsPrint);
				}
			}
		});
		t.start();

		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(final WindowEvent event) {
				t.interrupt();
				animator.stop();
			}
		});

		primaryStage.show();
	}

	private void render(final Rectangle[] rects) {
		final BUFFER_FORMATS format = jAER2fx.buffer.getFormat();

		if (format == BUFFER_FORMATS.BYTE) {
			for (int y = 0, point = 0; y < jAER2fx.YLEN; y++) {
				for (int x = 0; x < jAER2fx.XLEN; x++, point++) {
					if (format == BUFFER_FORMATS.BYTE) {
						rects[point].setFill(Color.rgb(((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF,
							(((ByteBuffer) jAER2fx.buffer.getBuffer()).get() == 0) ? (0.0) : (1.0)));
					}
					else {
						rects[point].setFill(Color.color(((FloatBuffer) jAER2fx.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fx.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fx.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fx.buffer.getBuffer()).get()));
					}
				}
			}
		}
		else {
			for (int y = 0, point = 0; y < jAER2fx.YLEN; y++) {
				for (int x = 0; x < jAER2fx.XLEN; x++, point++) {
					if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
						rects[point].setFill(Color.rgb(((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fx.buffer.getBuffer()).get() & 0xFF, 1.0));
					}
					else {
						rects[point].setFill(Color.color(((FloatBuffer) jAER2fx.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fx.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fx.buffer.getBuffer()).get(), 1.0));
					}
				}
			}
		}
	}
}
