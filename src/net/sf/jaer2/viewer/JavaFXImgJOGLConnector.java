package net.sf.jaer2.viewer;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import com.jogamp.common.nio.Buffers;

public class JavaFXImgJOGLConnector extends ImageView {
	private final WritableImage image;
	private final PixelWriter pxWriter;
	private final GLOffscreenAutoDrawable glOffscreenDrawable;
	private final GLEventListener readOutListener = new GLReadOutToImage();

	private static final int imageBufferBGRA8Number = 2;
	private final Semaphore syncImageBufferBGRA8Swap[] = new Semaphore[JavaFXImgJOGLConnector.imageBufferBGRA8Number];
	private final ByteBuffer imageBufferBGRA8[] = new ByteBuffer[JavaFXImgJOGLConnector.imageBufferBGRA8Number];
	private static final PixelFormat<ByteBuffer> pxFormat = PixelFormat.getByteBgraInstance();

	public JavaFXImgJOGLConnector(final int width, final int height) {
		super();

		image = new WritableImage(width, height);
		pxWriter = image.getPixelWriter();

		setImage(image);

		// Initialize image buffers for pixel data transfer.
		for (int i = 0; i < JavaFXImgJOGLConnector.imageBufferBGRA8Number; i++) {
			syncImageBufferBGRA8Swap[i] = new Semaphore(1);
			imageBufferBGRA8[i] = Buffers.newDirectByteBuffer(4 * width * height);
		}

		GLProfile.initSingleton();
		final GLProfile glp = GLProfile.get(GLProfile.GL2);

		final GLCapabilities caps = new GLCapabilities(glp);
		caps.setOnscreen(false);
		caps.setHardwareAccelerated(true);
		caps.setFBO(true);

		final GLDrawableFactory factory = GLDrawableFactory.getFactory(caps.getGLProfile());

		glOffscreenDrawable = factory.createOffscreenAutoDrawable(null, caps, null, width, height, null);
		glOffscreenDrawable.setAutoSwapBufferMode(true);

		glOffscreenDrawable.display();

		glOffscreenDrawable.addGLEventListener(readOutListener);
	}

	public synchronized void addGLEventListener(final GLEventListener listener) {
		// Add the new listener at the end of the queue, but before the readOutListener.
		glOffscreenDrawable.addGLEventListener(getGLEventListenerCount(), listener);
	}

	public synchronized void addGLEventListener(final int index, final GLEventListener listener)
		throws IndexOutOfBoundsException {
		// Index can never be negative or bigger than the count (minus the readOutListener).
		if ((index < 0) || (index > getGLEventListenerCount())) {
			throw new IndexOutOfBoundsException();
		}

		// Add new listener at specified index.
		glOffscreenDrawable.addGLEventListener(index, listener);
	}

	public synchronized GLEventListener getGLEventListener(final int index) throws IndexOutOfBoundsException {
		// Index can never be negative or bigger than the count (minus the readOutListener).
		if ((index < 0) || (index > getGLEventListenerCount())) {
			throw new IndexOutOfBoundsException();
		}

		// Get new listener from specified index.
		return glOffscreenDrawable.getGLEventListener(index);
	}

	public synchronized int getGLEventListenerCount() {
		// Transparently remove the readout listener.
		return (glOffscreenDrawable.getGLEventListenerCount() - 1);

	}

	public synchronized GLEventListener removeGLEventListener(final GLEventListener listener) {
		// Never remove the readOutListener.
		if (listener != readOutListener) {
			return glOffscreenDrawable.removeGLEventListener(listener);
		}

		return null;
	}

	public synchronized void display() {
		glOffscreenDrawable.display();
	}

	private class GLReadOutToImage implements GLEventListener {
		@Override
		public void display(final GLAutoDrawable drawable) {
			final GL2 gl = drawable.getGL().getGL2();

			// Read back final result.
			int selectedImageBuffer = 0;

			while (!syncImageBufferBGRA8Swap[selectedImageBuffer].tryAcquire()) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}

				selectedImageBuffer = (selectedImageBuffer + 1) % JavaFXImgJOGLConnector.imageBufferBGRA8Number;
			}

			gl.glReadBuffer(GL.GL_FRONT);
			gl.glReadPixels(0, 0, (int) image.getWidth(), (int) image.getHeight(), GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
				imageBufferBGRA8[selectedImageBuffer]);

			final int releaseSelectedImageBuffer = selectedImageBuffer;

			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					// Write current buffer out.
					pxWriter.setPixels(0, 0, (int) image.getWidth(), (int) image.getHeight(),
						JavaFXImgJOGLConnector.pxFormat, imageBufferBGRA8[releaseSelectedImageBuffer],
						((int) image.getWidth()) * 4);

					syncImageBufferBGRA8Swap[releaseSelectedImageBuffer].release();
				}
			});
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
			gl.glOrthof(0, (int) image.getWidth(), 0, (int) image.getHeight(), -1, 1);

			gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
			gl.glLoadIdentity();

			gl.glViewport(0, 0, (int) image.getWidth(), (int) image.getHeight());
		}

		@Override
		public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int arg3,
			final int arg4) {
		}
	}
}
