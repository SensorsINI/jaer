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
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import com.jogamp.common.nio.Buffers;

public final class JavaFXImgJOGLConnector extends ImageView {
	protected final WritableImage image;
	protected final PixelWriter pxWriter;

	private static final int glOffscreenDrawableNumber = 8;
	protected final Semaphore syncGLOffscreenDrawable[] = new Semaphore[JavaFXImgJOGLConnector.glOffscreenDrawableNumber];
	protected final GLOffscreenAutoDrawable glOffscreenDrawable[] = new GLOffscreenAutoDrawable[JavaFXImgJOGLConnector.glOffscreenDrawableNumber];
	private final GLReadOutToImage glReadOutToImage[] = new GLReadOutToImage[JavaFXImgJOGLConnector.glOffscreenDrawableNumber];

	private static final int imageBufferNumber = 4;
	protected final Semaphore syncImageBuffer[] = new Semaphore[JavaFXImgJOGLConnector.imageBufferNumber];
	protected final ByteBuffer imageBuffer[] = new ByteBuffer[JavaFXImgJOGLConnector.imageBufferNumber];

	public JavaFXImgJOGLConnector(final int width, final int height) {
		super();

		image = new WritableImage(width, height);
		pxWriter = image.getPixelWriter();

		setImage(image);

		// Initialize image buffers for pixel data transfer.
		for (int i = 0; i < JavaFXImgJOGLConnector.imageBufferNumber; i++) {
			syncImageBuffer[i] = new Semaphore(1);
			imageBuffer[i] = Buffers.newDirectByteBuffer(4 * width * height);
		}

		GLProfile.initSingleton();
		final GLProfile glp = GLProfile.get(GLProfile.GL2);

		final GLCapabilities caps = new GLCapabilities(glp);
		caps.setOnscreen(false);
		caps.setHardwareAccelerated(true);
		caps.setFBO(true);

		final GLDrawableFactory factory = GLDrawableFactory.getFactory(caps.getGLProfile());

		for (int i = 0; i < JavaFXImgJOGLConnector.glOffscreenDrawableNumber; i++) {
			syncGLOffscreenDrawable[i] = new Semaphore(1);
			glOffscreenDrawable[i] = factory.createOffscreenAutoDrawable(null, caps, null, width, height, null);
			glReadOutToImage[i] = new GLReadOutToImage();

			glOffscreenDrawable[i].setAutoSwapBufferMode(true);
			glOffscreenDrawable[i].display();
			glOffscreenDrawable[i].addGLEventListener(glReadOutToImage[i]);
		}
	}

	public synchronized GLAutoDrawable getDrawable() {
		int selectedGLOffscreenDrawable = 0;

		while (!syncGLOffscreenDrawable[selectedGLOffscreenDrawable].tryAcquire()) {
			if (Thread.currentThread().isInterrupted()) {
				return null;
			}

			selectedGLOffscreenDrawable++;

			if (selectedGLOffscreenDrawable >= JavaFXImgJOGLConnector.glOffscreenDrawableNumber) {
				return null;
			}
		}

		glReadOutToImage[selectedGLOffscreenDrawable].releaseSelectedGLOffscreenDrawable = selectedGLOffscreenDrawable;
		glOffscreenDrawable[selectedGLOffscreenDrawable].getContext().makeCurrent();

		return glOffscreenDrawable[selectedGLOffscreenDrawable];
	}

	private final class GLReadOutToImage implements GLEventListener {
		protected final PixelFormat<ByteBuffer> pxFormat = PixelFormat.getByteBgraPreInstance();
		public int releaseSelectedGLOffscreenDrawable = 0;

		public GLReadOutToImage() {
		}

		@Override
		public void display(final GLAutoDrawable drawable) {
			drawable.getContext().makeCurrent();

			final GL2 gl = drawable.getGL().getGL2();

			// Read back final result.
			int selectedImageBuffer = 0;

			while (!syncImageBuffer[selectedImageBuffer].tryAcquire()) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}

				selectedImageBuffer = (selectedImageBuffer + 1) % JavaFXImgJOGLConnector.imageBufferNumber;
			}

			gl.glReadBuffer(GL.GL_FRONT);
			gl.glReadPixels(0, 0, (int) image.getWidth(), (int) image.getHeight(), GL.GL_BGRA,
				GL2GL3.GL_UNSIGNED_INT_8_8_8_8_REV, imageBuffer[selectedImageBuffer]);

			drawable.getContext().release();

			glOffscreenDrawable[releaseSelectedGLOffscreenDrawable].getContext().release();
			syncGLOffscreenDrawable[releaseSelectedGLOffscreenDrawable].release();

			final int releaseSelectedImageBuffer = selectedImageBuffer;

			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					// Write current buffer out.
					pxWriter.setPixels(0, 0, (int) image.getWidth(), (int) image.getHeight(), pxFormat,
						imageBuffer[releaseSelectedImageBuffer], ((int) image.getWidth()) * 4);

					syncImageBuffer[releaseSelectedImageBuffer].release();
				}
			});
		}

		@SuppressWarnings("unused")
		@Override
		public void dispose(final GLAutoDrawable drawable) {
			// Empty, no implementation yet.
		}

		@Override
		public void init(final GLAutoDrawable drawable) {
			final GL2 gl = drawable.getGL().getGL2();

			gl.setSwapInterval(0);

			gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glOrthof(0, (int) image.getWidth(), (int) image.getHeight(), 0, 1, -1);

			gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
			gl.glLoadIdentity();
		}

		@SuppressWarnings("unused")
		@Override
		public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int arg3,
			final int arg4) {
			// Empty, no implementation yet.
		}
	}
}
