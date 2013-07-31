package net.sf.jaer2.viewer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

public class jAER2fxcanvas extends Application {
	private static long FPS = 0;
	private static final int RSIZE = 2;
	private static final int XLEN = 640;
	private static final int YLEN = 480;

	private static final BufferWorks buffer = new BufferWorks(jAER2fxcanvas.XLEN, jAER2fxcanvas.YLEN,
		BUFFER_FORMATS.BYTE_NOALPHA, 0);

	private static final boolean USE_PIXELWRITER = true;
	private static final PixelFormat<ByteBuffer> pxFormat = PixelFormat.getByteRgbInstance();

	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start()
		// when ready.
		Application.launch(args);
	}

	@Override
	public void start(final Stage primaryStage) {
		final StackPane root = new StackPane();

		final Canvas canvas = new Canvas(jAER2fxcanvas.XLEN, jAER2fxcanvas.YLEN);
		final GraphicsContext gc = canvas.getGraphicsContext2D();
		final PixelWriter pxWriter = gc.getPixelWriter();
		root.getChildren().add(canvas);

		canvas.setScaleX(jAER2fxcanvas.RSIZE);
		canvas.setScaleY(jAER2fxcanvas.RSIZE);

		final AnimationTimer animator = new AnimationTimer() {
			@Override
			public void handle(final long time) {
				jAER2fxcanvas.FPS++;

				jAER2fxcanvas.buffer.update();

				render(gc, pxWriter);
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

					final long fpsPrint = jAER2fxcanvas.FPS / ((System.currentTimeMillis() - start) / 1000);
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

	private void render(final GraphicsContext gc, final PixelWriter pxWriter) {
		final BUFFER_FORMATS format = jAER2fxcanvas.buffer.getFormat();

		if (format == BUFFER_FORMATS.BYTE) {
			for (int y = 0; y < jAER2fxcanvas.YLEN; y++) {
				for (int x = 0; x < jAER2fxcanvas.XLEN; x++) {
					if (format == BUFFER_FORMATS.BYTE) {
						gc.setFill(Color.rgb(((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF,
							(((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() == 0) ? (0.0) : (1.0)));
					}
					else {
						gc.setFill(Color.color(((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get()));
					}

					gc.fillRect(x, y, 1, 1);
				}
			}
		}
		else if (jAER2fxcanvas.USE_PIXELWRITER && (format == BUFFER_FORMATS.BYTE_NOALPHA)) {
			pxWriter.setPixels(0, 0, jAER2fxcanvas.XLEN, jAER2fxcanvas.YLEN, jAER2fxcanvas.pxFormat,
				(ByteBuffer) jAER2fxcanvas.buffer.getBuffer(), jAER2fxcanvas.XLEN * 3);
		}
		else {
			for (int y = 0; y < jAER2fxcanvas.YLEN; y++) {
				for (int x = 0; x < jAER2fxcanvas.XLEN; x++) {
					if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
						gc.setFill(Color.rgb(((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF,
							((ByteBuffer) jAER2fxcanvas.buffer.getBuffer()).get() & 0xFF, 1.0));
					}
					else {
						gc.setFill(Color.color(((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(),
							((FloatBuffer) jAER2fxcanvas.buffer.getBuffer()).get(), 1.0));
					}

					gc.fillRect(x, y, 1, 1);
				}
			}
		}
	}
}
