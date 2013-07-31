package net.sf.jaer2.viewer;

import java.nio.ByteBuffer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

public class jAER2fximg extends Application {
	private static long FPS = 0;
	private static final int RSIZE = 2;
	private static final int XLEN = 640;
	private static final int YLEN = 480;

	private static final BufferWorks buffer = new BufferWorks(jAER2fximg.XLEN, jAER2fximg.YLEN,
		BUFFER_FORMATS.BYTE_NOALPHA, 0);

	private static final PixelFormat<ByteBuffer> pxFormat = PixelFormat.getByteRgbInstance();
	private static final WritableImage img = new WritableImage(jAER2fximg.XLEN, jAER2fximg.YLEN);
	private static final PixelWriter pxWriter = jAER2fximg.img.getPixelWriter();

	public static void main(final String[] args) {
		// Launch the JavaFX application: do initialization and call start()
		// when ready.
		Application.launch(args);
	}

	@Override
	public void start(final Stage primaryStage) {
		final StackPane root = new StackPane();

		final ImageView imgView = new ImageView(jAER2fximg.img);
		root.getChildren().add(imgView);

		imgView.setScaleX(jAER2fximg.RSIZE);
		imgView.setScaleY(jAER2fximg.RSIZE);

		final AnimationTimer animator = new AnimationTimer() {
			@Override
			public void handle(final long time) {
				jAER2fximg.FPS++;

				jAER2fximg.buffer.update();

				render();
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

					final long fpsPrint = jAER2fximg.FPS / ((System.currentTimeMillis() - start) / 1000);
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

	private void render() {
		final BUFFER_FORMATS format = jAER2fximg.buffer.getFormat();

		if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
			jAER2fximg.pxWriter.setPixels(0, 0, jAER2fximg.XLEN, jAER2fximg.YLEN, jAER2fximg.pxFormat,
				(ByteBuffer) jAER2fximg.buffer.getBuffer(), jAER2fximg.XLEN * 3);
		}
	}
}
