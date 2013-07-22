package net.sf.jaer2.viewer;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.ByteBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.swing.JFrame;

import net.sf.jaer2.viewer.BufferWorks.BUFFER_FORMATS;

import com.jogamp.opengl.util.Animator;

public class jAER2jogl implements GLEventListener {
	private static long FPS = 0;
	private static final int RSIZE = 2;
	private static final int XLEN = 640;
	private static final int YLEN = 480;

	private static final BufferWorks buffer = new BufferWorks(jAER2jogl.XLEN, jAER2jogl.YLEN,
		BUFFER_FORMATS.BYTE_NOALPHA, 0);

	private static final boolean USE_QUADS = false;

	public static void main(final String[] args) {
		GLProfile.initSingleton();
		final GLProfile glp = GLProfile.get(GLProfile.GL2);
		final GLCapabilities caps = new GLCapabilities(glp);
		final GLCanvas canvas = new GLCanvas(caps);

		final JFrame jframe = new JFrame("jAER2 JOGL Test");
		jframe.getContentPane().add(canvas, BorderLayout.CENTER);
		jframe.setSize(1920, 1080);
		jframe.setVisible(true);

		canvas.addGLEventListener(new jAER2jogl());

		final Animator animator = new Animator();
		animator.add(canvas);
		animator.setRunAsFastAsPossible(true);
		animator.start();

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

					final long fpsPrint = jAER2jogl.FPS / ((System.currentTimeMillis() - start) / 1000);
					System.out.println("FPS are: " + fpsPrint);
				}
			}
		});
		t.start();

		jframe.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent windowevent) {
				t.interrupt();
				animator.stop();
				jframe.dispose();
				System.exit(0);
			}
		});
	}

	@Override
	public void display(final GLAutoDrawable drawable) {
		jAER2jogl.FPS++;

		jAER2jogl.buffer.update();

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
		final BUFFER_FORMATS format = jAER2jogl.buffer.getFormat();
		final int positionJump = (format == BUFFER_FORMATS.BYTE) ? (4) : (3);
		final GL2 gl = drawable.getGL().getGL2();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT);

		// Use either quads or rectangles.
		if (jAER2jogl.USE_QUADS) {
			for (int y = 0; y < jAER2jogl.YLEN; y++) {
				for (int x = 0; x < jAER2jogl.XLEN; x++) {
					gl.glPushMatrix();

					gl.glTranslatef(x * jAER2jogl.RSIZE, y * jAER2jogl.RSIZE, 0);

					gl.glBegin(GL2GL3.GL_QUADS);

					if (format == BUFFER_FORMATS.BYTE) {
						gl.glColor4ubv((ByteBuffer) jAER2jogl.buffer.getBuffer());
					}
					else {
						gl.glColor3ubv((ByteBuffer) jAER2jogl.buffer.getBuffer());
					}

					// Advance color buffer on each iteration
					jAER2jogl.buffer.getBuffer().position(jAER2jogl.buffer.getBuffer().position() + positionJump);

					gl.glVertex3f(0, 0, 0);
					gl.glVertex3f(0, jAER2jogl.RSIZE, 0);
					gl.glVertex3f(jAER2jogl.RSIZE, jAER2jogl.RSIZE, 0);
					gl.glVertex3f(jAER2jogl.RSIZE, 0, 0);

					gl.glEnd();

					gl.glPopMatrix();
				}
			}
		}
		else {
			for (int y = 0; y < jAER2jogl.YLEN; y++) {
				for (int x = 0; x < jAER2jogl.XLEN; x++) {
					if (format == BUFFER_FORMATS.BYTE) {
						gl.glColor4ubv((ByteBuffer) jAER2jogl.buffer.getBuffer());
					}
					else {
						gl.glColor3ubv((ByteBuffer) jAER2jogl.buffer.getBuffer());
					}

					// Advance color buffer on each iteration
					jAER2jogl.buffer.getBuffer().position(jAER2jogl.buffer.getBuffer().position() + positionJump);

					gl.glRectf(x * jAER2jogl.RSIZE, y * jAER2jogl.RSIZE, (x * jAER2jogl.RSIZE) + jAER2jogl.RSIZE,
						(y * jAER2jogl.RSIZE) + jAER2jogl.RSIZE);
				}
			}
		}

		gl.glFlush();
	}
}
