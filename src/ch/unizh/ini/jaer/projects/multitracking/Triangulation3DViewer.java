package ch.unizh.ini.jaer.projects.multitracking;

import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.jblas.FloatMatrix;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;

import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;

public class Triangulation3DViewer extends DisplayMethod implements GLEventListener,FrameAnnotater {
	public Triangulation3DViewer(ChipCanvas parent) {
		super(parent);

	}
	public int XSize=chip.getSizeX();
	public int YSize=chip.getSizeY();

	protected DisplayMethod displayMethod;
	private GLU glu = new GLU();
	private float sizeOfWin=75f;
	private float radius=10;
	public Vector<LinkedList<FloatMatrix>> Xfinals=new Vector<LinkedList<FloatMatrix>>();
	private boolean annotationEnabled=true;
	public GLCanvas glcanvas;
	private float anglex;
	private float angley;
	private float scaleChipPixels2ScreenPixels;
	protected Preferences prefs = Preferences.userNodeForPackage(ChipCanvas.class);
	//	private float origin3dx = prefs.getInt("ChipCanvas.origin3dx", 0);
	//	private float origin3dy = prefs.getInt("ChipCanvas.origin3dy", 0);
	//private Zoom zoom = new Zoom();
	protected boolean zoomMode = false; // true while user is dragging zoom box
	private InputMap im=new InputMap();
	private ActionMap am=new ActionMap();
	private int Height;
	private int Width;
	private float scale=1f;
	private FPSAnimator animator;
	private FloatMatrix positionSecondCamera;
	private int xeye=0;
	private int yeye=200;
	private int zeye=600;
	public Semaphore semaphore;
	// reused imageOpenGL for OpenGL image grab
	@Override
	public void display( GLAutoDrawable drawable ) {
		final GL2 gl = drawable.getGL().getGL2();
		setCamera(drawable, glu, zeye);
		//gl.glEnable(GL.GL_DEPTH_TEST);
		//gl.glTranslatef( -chip.getSizeX()/4, -chip.getSizeX()/4, -1000);
		//gl.glTranslatef( chip.getSizeX()/2, chip.getSizeX()/2, chip.getSizeX()/2 );
		//gl.glRotatef(45, 0, 1, 0);
		// rotate and align viewpoint
		gl.glRotatef(chip.getCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
		gl.glRotatef(chip.getCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector
		//gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), -chip.getSizeX()/2);

		//		      gl.glBegin( GL.GL_LINES );
		//		      gl.glVertex3f( -sizeOfWin,0f,0 );
		//		      gl.glVertex3f( 0f,-sizeOfWin, 0 );
		//		      gl.glEnd();

		//3d line
		//		      gl.glBegin( GL.GL_LINES );
		//		      gl.glVertex3f( -sizeOfWin,0f,3f );// 3 units into the window
		//		      gl.glVertex3f( 0f,-sizeOfWin, 3f );
		//		      gl.glEnd();

		// Floor
		gl.glBegin(GL2ES3.GL_QUADS);
		gl.glColor3f(0.2f, 0.2f, 0.2f);
		gl.glVertex3f(-chip.getSizeX(), 0.0f, -chip.getSizeX());
		gl.glVertex3f(-chip.getSizeX(), 0.0f, chip.getSizeX());
		gl.glVertex3f(chip.getSizeX(), 0.0f, chip.getSizeX());
		gl.glVertex3f(chip.getSizeX(), 0.0f, -chip.getSizeX());
		gl.glEnd();


		// First face
		gl.glColor3f(0, 0, 1);
		gl.glLineWidth(2.0f);
		gl.glBegin(GL.GL_LINES);
		//gl.glVertex3f(0, 0, 0);
		gl.glVertex3f(-chip.getSizeX(), 0, 0);
		gl.glVertex3f(chip.getSizeX(), 0, 0);
		//              gl.glVertex3f(chip.getSizeX()/2, chip.getSizeY(), 0);
		//              gl.glVertex3f(0, chip.getSizeY(), 0);
		//              gl.glVertex3f(0, 0, 0);
		gl.glEnd();

		// Second face
		gl.glColor3f(1, 0, 0);
		gl.glLineWidth(2.0f);
		gl.glBegin(GL.GL_LINES);
		//              gl.glVertex3f(0, 0, 0);
		//              gl.glVertex3f(0, 0, chip.getSizeX()/2);
		//              gl.glVertex3f(0, chip.getSizeY(), chip.getSizeX()/2);
		gl.glVertex3f(0, -chip.getSizeX(), 0);
		gl.glVertex3f(0, chip.getSizeX(), 0);
		//gl.glVertex3f(0, 0, 0);
		gl.glEnd();

		// Second face
		gl.glColor3f(0, 1, 0);
		gl.glLineWidth(2.0f);
		gl.glBegin(GL.GL_LINES);
		//              gl.glVertex3f(0, 0, 0);
		//              gl.glVertex3f(0, 0, chip.getSizeX()/2);
		//              gl.glVertex3f(0, chip.getSizeY(), chip.getSizeX()/2);
		gl.glVertex3f(0,0, -chip.getSizeX());
		gl.glVertex3f(0,0, chip.getSizeX());
		//gl.glVertex3f(0, 0, 0);
		gl.glEnd();

		//Position of the first camera
		gl.glColor3f(1, 0, 0);
		gl.glLineWidth(2.0f);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex3f(-10, 0, 0);
		gl.glVertex3f(-10, 20, 0);
		gl.glVertex3f(10, 20, 0);
		gl.glVertex3f(10, 0, 0);
		gl.glEnd();

		//Position of the second camera
		gl.glColor3f(1, 0, 0);
		gl.glLineWidth(2.0f);
		gl.glBegin(GL.GL_LINE_LOOP);
		gl.glVertex3f(positionSecondCamera.get(0)-10, positionSecondCamera.get(1), positionSecondCamera.get(2));
		gl.glVertex3f(positionSecondCamera.get(0)-10, positionSecondCamera.get(1)+20, positionSecondCamera.get(2));
		gl.glVertex3f(positionSecondCamera.get(0)+10, positionSecondCamera.get(1)+20, positionSecondCamera.get(2));
		gl.glVertex3f(positionSecondCamera.get(0)+10, positionSecondCamera.get(1), positionSecondCamera.get(2));
		gl.glEnd();




		if(Xfinals.size()!=0){
			try {
				semaphore.acquire();

			// System.out.println("annotate through display");
			//gl.glPushMatrix();

			gl.glLineWidth(4);
			for(int h=0;h<Xfinals.size(); h++){
				if(Xfinals.get(h).size()!=0){
					gl.glColor3f(h, h+1, h+2);
					for(int i=0;i<(Xfinals.get(h).size()-1); i++){
						if(Xfinals.get(h).get(i).rows==3){
						//centerX[i]=Xfinals.get(i).get(0);
						//System.out.println(centerX);
						//centerY[i]=Xfinals.get(i).get(1);
						//centerZ[i]=Xfinals.get(i).get(2);

						gl.glBegin(GL.GL_LINE_LOOP);
						//System.out.println(centerY);

						gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
						gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
						gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)+(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
						gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)+(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);
						gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);
						gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);

						gl.glEnd();

					   }
					}
				}
			}
			semaphore.release();
			}
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//gl.glScalef(chip.getSizeX()/2, chip.getSizeY(), chip.getSizeX());
	}
	public void setVectToDisplay(Vector<LinkedList<FloatMatrix>> xfinals2){
		this.Xfinals=xfinals2;
	}
	@Override
	public void dispose( GLAutoDrawable arg0 ) {
		//method body
	}

	@Override
	public void init( GLAutoDrawable drawable ) {
		// drawable.setGL(new DebugGL(drawable.getGL()));
		drawable.setAutoSwapBufferMode(true);
		final GL2 gl = drawable.getGL().getGL2();

		//	        log.info("OpenGL implementation is: " + gl.getClass().getName() + "\nGL_VENDOR: "
		//	                + gl.glGetString(GL.GL_VENDOR) + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER) + "\nGL_VERSION: "
		//	                + gl.glGetString(GL.GL_VERSION) // + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
		//	        );
		final float glVersion = Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0, 3));
		if (glVersion < 1.3f) {
			//	            log.warning("\n\n*******************\nOpenGL version "
			//	                    + glVersion
			//	                    + " < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
		}
		// System.out.println("GLU_EXTENSIONS: "+glu.gluGetString(GLU.GLU_EXTENSIONS));

		gl.setSwapInterval(1);
		gl.glShadeModel(GLLightingFunc.GL_FLAT);

		gl.glClearColor(0, 0, 0, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		gl.glLoadIdentity();

		gl.glRasterPos3f(0, 0, 0);
		gl.glColor3f(1, 1, 1);
		//	        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "Initialized display");
		// Start animator (which should be a field).
		animator = new FPSAnimator(glcanvas, 60);
		animator.start();
	}

	@Override
	public void reshape( GLAutoDrawable drawable, int x, int y, int width, int height ) {

		GL2 gl = drawable.getGL().getGL2();

		if( height <= 0 ) {
			height = 1;
		}

		final float h = ( float ) width / ( float ) height;
		gl.glViewport( 0, 0, width, height );
		gl.glMatrixMode( GLMatrixFunc.GL_PROJECTION );
		gl.glLoadIdentity();

		glu.gluPerspective( 45.0f, h, 1.0, 20.0 );
		gl.glMatrixMode( GLMatrixFunc.GL_MODELVIEW );
		gl.glLoadIdentity();
	}


	private void setCamera(GLAutoDrawable drawable, GLU glu, float distance) {
		GL2 gl = drawable.getGL().getGL2();

		// Change to projection matrix.
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity();
		//gl.glEnable(GL.GL_DEPTH_TEST);
		//gl.glTranslatef( 0f, 0f, 800);
		//gl.glTranslatef( chip.getSizeX()/2, chip.getSizeX()/2, chip.getSizeX()/2 );
		//gl.glRotatef(45, 0, 1, 0);

		// Perspective.
		float widthHeightRatio = getWidth() / getHeight();
		//glu.gluPerspective(60, widthHeightRatio, 200, 1200);
		glu.gluPerspective(60, widthHeightRatio, 50, 2000);
		glu.gluLookAt(xeye, yeye, distance, 0, 0, 0, 0, 1, 0);

		// Change back to model view matrix.
		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glLoadIdentity();

	}



	private float getHeight() {
		// TODO Auto-generated method stub
		return Height;
	}
	private float getWidth() {
		// TODO Auto-generated method stub
		return Width;
	}
        
        @SuppressWarnings("deprecation")
	public void startNewWindows() {

		//getting the capabilities object of GL2 profile
		final GLProfile profile = GLProfile.get( GLProfile.GL2 );
		GLCapabilities capabilities = new GLCapabilities(profile);
        semaphore=new Semaphore(1);
		// The canvas
		glcanvas = new GLCanvas( capabilities );
		//Triangulation3DViewer triview = new Triangulation3DViewer();
		glcanvas.addGLEventListener( this );
		Height=400;
		Width=400;
		glcanvas.setSize( 400, 400 );

		//creating frame
		final JFrame frame = new JFrame ("triangulation 3D renderer");
		final JPanel panel = new JPanel();
		frame.add(panel);
		panel.enable();
		//frame.getContentPane().add(panel);
		//panel.requestFocus();
		int mapName = JComponent.WHEN_IN_FOCUSED_WINDOW ;
		panel.getInputMap(mapName);
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(102, 0), "6");
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(100, 0), "4");
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(104, 0), "8");
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(98, 0), "2");
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(107, 0), "+");
		panel.getInputMap(mapName).put(KeyStroke.getKeyStroke(109, 0), "-");
		am= panel.getActionMap();


		panel.getActionMap().put("+", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				zeye=zeye-10;
				System.out.println("Key pressed zoom in");
			}


		});



		panel.getActionMap().put("-", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				zeye=zeye+10;
				System.out.println("Key pressed zoom out");
			}


		});


		panel.getActionMap().put("6", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				xeye=xeye+10;
				System.out.println("Key pressed right");
			}


		});

		panel.getActionMap().put("4", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				xeye=xeye-10;
				System.out.println("Key pressed left");
			}


		});

		panel.getActionMap().put("8", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				yeye=yeye+10;
				System.out.println("Key pressed up");
			}
		});

		panel.getActionMap().put("2", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				yeye=yeye-10;
				System.out.println("Key pressed down");
			}


		});

		//KeyListener l = null;

		//adding canvas to it
		//this.glcanvas.addKeyListener(l);
		//frame.add(panel);

		frame.getContentPane().add(glcanvas);
		frame.setSize(frame.getContentPane().getPreferredSize() );
		frame.setVisible( true );
	}//end of main


	private void translateRight(GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();
		gl.glTranslatef(10, 0, 0);

	}
	private void translateLeft(GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();
		gl.glTranslatef(-10, 0, 0);

	}
	private void translateUp(GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();
		gl.glTranslatef(0,10, 0);

	}
	private void translateDown(GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();
		gl.glTranslatef(0,-10, 0);

	}


	@Override
	public void setAnnotationEnabled(boolean yes) {
		annotationEnabled=true;

	}

	@Override
	public boolean isAnnotationEnabled() {
		// TODO Auto-generated method stub
		return annotationEnabled;
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		final GL2 gl = drawable.getGL().getGL2();
		if(Xfinals.size()!=0){
			// System.out.println("annotate");
			//gl.glPushMatrix();
			gl.glColor3f(0, 0, 1);
			gl.glLineWidth(4);
			for(int h=0;h<Xfinals.size(); h++){
				for(int i=0;i<Xfinals.get(h).size(); i++){
					//centerX[i]=Xfinals.get(i).get(0);
					//System.out.println(centerX);
					//centerY[i]=Xfinals.get(i).get(1);
					//centerZ[i]=Xfinals.get(i).get(2);

					gl.glBegin(GL.GL_LINE_LOOP);
					//System.out.println(centerY);

					gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
					gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
					gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)+(radius/2))*scale, (Xfinals.get(h).get(i).get(2)-(radius/2))*scale);
					gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)+(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);
					gl.glVertex3f((Xfinals.get(h).get(i).get(0)-(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);
					gl.glVertex3f((Xfinals.get(h).get(i).get(0)+(radius/2))*scale, (Xfinals.get(h).get(i).get(1)-(radius/2))*scale, (Xfinals.get(h).get(i).get(2)+(radius/2))*scale);

					gl.glEnd();

				}
				//gl.glPopMatrix();
			}
		}
	}
	public void repaint() {
		glcanvas.display();
	}

	/**
	 * calls repaint on the drawable
	 *
	 * @param tm time to repaint within, in ms
	 */
	public void repaint(final long tm) {
		glcanvas.repaint(tm);
	}
	public void preventNewEvent() {
		annotate(this.glcanvas);
	}
	//	public float getAnglex() {
	//        return anglex;
	//    }
	//
	//    public float getAngley() {
	//        return angley;
	//    }
	//
	//
	//    /**
	//     * Pixel drawing scale. 1 pixel is rendered to getScale() screen pixels. To
	//     * obtain chip pixel units from screen pixels, divide screen pixels by
	//     * <code>getScale()</code>. Conversely, to scale chip pixels to screen
	//     * pixels, multiply by <code>getScale()</code>.
	//     *
	//     * @return scale in screen pixels/chip pixel.
	//     */
	//    public float getScale() {
	//        return scaleChipPixels2ScreenPixels;
	//    }
	//
	//    /**
	//     * A utility method that returns an AWT Color from float rgb values
	//     */
	//
	//
	//    private boolean mouseWasInsideChipBounds = true;
	//
	//    /**
	//     * Finds the chip pixel from a ChipCanvas point. From
	//     * <a href="http://processing.org/discourse/yabb_beta/YaBB.cgi?board=OpenGL;action=display;num=1176483247">this
	//     * forum link</a>.
	//     *
	//     * @param mp a Point in ChipCanvas pixels.
	//     * @return the AEChip pixel, clipped to the bounds of the AEChip.
	//     */
	//    public Point getPixelFromPoint(final Point mp) {
	//        final double wcoord[] = new double[3];// wx, wy, wz;// returned xyz coords
	//        // this method depends on current GL context being the one that is used for rendering.
	//        // the display method should not push/pop the matrix stacks!!
	//        if (mp == null) {
	//            // log.warning("null Point (outside entire canvas?), returning center pixel");
	//            return new Point(chip.getSizeX() / 2, chip.getSizeY() / 2);
	//        }
	////        synchronized (drawable.getTreeLock()) {
	//            try {
	//
	//                final int ret = glcanvas.getContext().makeCurrent();
	//                if (ret != GLContext.CONTEXT_CURRENT) {
	//                    throw new GLException("couldn't make context current");
	//                }
	//
	//                final int viewport[] = new int[4];
	//                final double mvmatrix[] = new double[16];
	//                final double projmatrix[] = new double[16];
	//                int realy = 0;// GL y coord pos
	//                // set up a floatbuffer to get the depth buffer value of the mouse position
	//                final FloatBuffer fb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	//                final GL2 gl = glcanvas.getContext().getGL().getGL2();
	//                checkGLError(gl, glu, "before getting mouse point");
	//                gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
	//                gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX, mvmatrix, 0);
	//                gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, projmatrix, 0);
	//                /* note viewport[3] is height of window in pixels */
	//                realy = viewport[3] - (int) mp.getY() - 1;
	//                // Get the depth buffer value at the mouse position. have to do height-mouseY, as GL puts 0,0 in the bottom
	//                // left, not top left.
	//                checkGLError(gl, glu, "after getting modelview and projection matrices in getMousePoint");
	////                gl.glReadPixels(mp.x, realy, 1, 1, GL2ES2.GL_DEPTH_COMPONENT, GL.GL_FLOAT, fb);
	////                checkGLError(gl, glu, "after readPixels in getMousePoint");
	//                final float z = 0; // fb.getString(0); // assume we want z=0 value of mouse point
	//                glu.gluUnProject(mp.getX(), realy, z, mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
	//                checkGLError(gl, glu, "after gluUnProject in getting mouse point");
	//            } catch (final GLException e) {
	//                log.warning("couldn't make GL context current, mouse position meaningless: " + e.toString());
	//            } finally {
	//            	glcanvas.getContext().release();
	//            }
	////        }
	//        final Point p = new Point();
	//        p.x = (int) Math.round(wcoord[0]);
	//        p.y = (int) Math.round(wcoord[1]);
	//        if ((p.x < 0) || (p.x > (chip.getSizeX() - 1)) || ((p.y < 0) | (p.y > (chip.getSizeY() - 1)))) {
	//            mouseWasInsideChipBounds = false;
	//        } else {
	//            mouseWasInsideChipBounds = true;
	//        }
	//        clipPoint(p);
	//
	//        // log.info("Mouse xyz=" + mp.getX() + "," + realy + "," + z + "   Pixel x,y=" + p.x + "," + p.y);
	//        return p;
	//    }
	//
	//    /**
	//     * Finds the current AEChip pixel mouse position, or center of array if not
	//     * inside.
	//     *
	//     * @return the AEChip pixel, clipped to the bounds of the AEChip
	//     */
	//    public Point getMousePixel() {
	//        final Point mp = glcanvas.getMousePosition();
	//        return getPixelFromPoint(mp);
	//    }
	//
	//    /**
	//     * Returns state of mouse from last call to getPixelFromPoint; true if mouse
	//     * inside bounds of chip drawing area.
	//     *
	//     * @return true if was inside, false otherwise.
	//     */
	//    public boolean wasMousePixelInsideChipBounds() {
	//        return mouseWasInsideChipBounds;
	//    }
	//
	//    /**
	//     * Takes a MouseEvent and returns the AEChip pixel.
	//     *
	//     * @return pixel x,y location (integer point) from MouseEvent. Accounts for
	//     * scaling and borders of chip display area
	//     */
	//    public Point getPixelFromMouseEvent(final MouseEvent evt) {
	//        final Point mp = evt.getPoint();
	//        return getPixelFromPoint(mp);
	//    }
	//
	//
	//    private Point mouseDragStartPoint = new Point(0, 0);
	//    private Point origin3dMouseDragStartPoint = new Point(0, 0);
	//
	//
	//    protected void initComponents() {
	//        unzoom();
	//        if (getRenderer() != null) {
	//        	glcanvas.addMouseListener(new MouseAdapter() {
	//
	//                @Override
	//                public void mousePressed(final MouseEvent evt) {
	//                    mouseDragStartPoint.setLocation(evt.getPoint());
	//                    origin3dMouseDragStartPoint.setLocation(origin3dx, origin3dy);
	//
	//                    final Point p = getPixelFromMouseEvent(evt);
	//					// if (!isZoomMode()) {
	//
	//                    // System.out.println("evt="+evt);
	//                    if (evt.getButton() == 1) {
	//                        getRenderer().setXsel((short) -1);
	//                        getRenderer().setYsel((short) -1);
	//                        // System.out.println("cleared pixel selection");
	//                    } else if (evt.getButton() == 3) {
	//						// we want mouse click location in chip pixel location
	//                        // don't forget that there is a borderSpacePixels on the orthographic viewport projection
	//                        // this border means that the pixels are actually drawn on the screen in a viewport that has a
	//                        // borderSpacePixels sized edge on all sides
	//                        // for simplicity because i can't figure this out, i have set the borderSpacePixels to zero
	//
	//                        // log.info(" width=" + drawable.getWidth() + " height=" + drawable.getHeight() + " mouseX=" +
	//                        // evt.getX() + " mouseY=" + evt.getY() + " xt=" + xt + " yt=" + yt);
	//                        // renderer.setXsel((short)((0+((evt.x-xt-borderSpacePixels)/j2dScale))));
	//                        // renderer.setYsel((short)(0+((getPheight()-evt.y+yt-borderSpacePixels)/j2dScale)));
	//                        getRenderer().setXsel((short) p.x);
	//                        getRenderer().setYsel((short) p.y);
	//                        log.info("Selected pixel x,y=" + getRenderer().getXsel() + "," + getRenderer().getYsel());
	//                    }
	//                    // } else if (isZoomMode()) { // zoom startZoom
	//                    // zoom.startZoom(p);
	//                    // }
	//                }
	//
	//                @Override
	//                public void mouseReleased(final MouseEvent e) {
	//                    if (is3DEnabled()) {
	//                        log.info("3d rotation: angley=" + angley + " anglex=" + anglex + " 3d origin: x="
	//                                + getOrigin3dx() + " y=" + getOrigin3dy());
	//                    }
	//                    // else
	//                    // if (isZoomMode()) {
	//                    // Point p = getPixelFromMouseEvent(e);
	//                    // zoom.endZoom(p);
	//                    // // zoom.endX=p.x;
	//                    // // zoom.endY=p.y;
	//                    // // setZoomMode(false);
	//                    // }
	//                }
	//            });
	//        } // renderer!=null
	//
	//        glcanvas.addMouseMotionListener(new MouseMotionListener() {
	//
	//            private Object origin3dx;
	//			private Object origin3dy;
	//
	//			@Override
	//            public void mouseDragged(final MouseEvent e) {
	////                                Point p=getPixelFromMouseEvent(e);
	//                final int x = e.getX();
	//                final int y = e.getY();
	//                final int but1mask = InputEvent.BUTTON1_DOWN_MASK, but3mask = InputEvent.BUTTON3_DOWN_MASK;
	//                if ((e.getModifiersEx() & but1mask) == but1mask) {
	//                    if (is3DEnabled()) {
	//                        final float maxAngle = 180f;
	//                        setAngley((maxAngle * (x - (glcanvas.getWidth() / 2))) / glcanvas.getWidth());
	//                        setAnglex((maxAngle * (y - (glcanvas.getHeight() / 2))) / glcanvas.getHeight());
	//                    } else if (isZoomMode()) {
	//                        // System.out.print("z");
	//                    }
	//                } else if ((e.getModifiersEx() & but3mask) == but3mask) {
	//                    if (is3DEnabled()) {
	//                        // mouse position x,y is in pixel coordinates in window canvas, but later on, the events will be
	//                        // drawn in
	//                        // chip coordinates (transformation applied). therefore here we set origin in pixel coordinates
	//                        // based on mouse
	//                        // position in window.
	//                        float dx = e.getX() - mouseDragStartPoint.x;
	//                        float dy = e.getY() - mouseDragStartPoint.y;
	//                        origin3dx = origin3dMouseDragStartPoint.x + Math.round((chip.getMaxSize() * (dx)) / glcanvas.getWidth());
	//                        origin3dy = origin3dMouseDragStartPoint.y + Math.round((chip.getMaxSize() * (-dy)) / glcanvas.getHeight());
	//                    }
	//                }
	//                repaint(100);
	//                // log.info("repaint called for");
	//            }
	//
	//            @Override
	//            public void mouseMoved(final MouseEvent e) {
	//            }
	//        });
	//    }
	//    public boolean is3DEnabled() {
	//        return displayMethod instanceof DisplayMethod3D;
	//    }
	//
	//    public boolean isZoomMode() {
	//        return zoomMode;
	//    }
	//    protected void set3dOrigin(final int x, final int y) {
	//        setOrigin3dy(y);
	//        setOrigin3dx(x);
	//        prefs.putInt("ChipCanvas.origin3dx", x);
	//        prefs.putInt("ChipCanvas.origin3dy", y);
	//    }
	//
	//    public void setAnglex(final float anglex) {
	//        this.anglex = anglex;
	//        prefs.putFloat("ChipCanvas.anglex", anglex);
	//    }
	//
	//    public void setAngley(final float angley) {
	//        this.angley = angley;
	//        prefs.putFloat("ChipCanvas.angley", angley);
	//    }
	//    public float getOrigin3dx() {
	//        return origin3dx;
	//    }
	//
	//    public void setOrigin3dx(final float origin3dx) {
	//        this.origin3dx = origin3dx;
	//    }
	//
	//    public float getOrigin3dy() {
	//        return origin3dy;
	//    }
	//
	//    public void setOrigin3dy(final float origin3dy) {
	//        this.origin3dy = origin3dy;
	//    }
	//    public void unzoom() {
	//        getZoom().unzoom();
	//    }
	//    public Zoom getZoom() {
	//        return zoom;
	//    }
	//
	//    public void checkGLError(final GL2 g, final GLU glu, final String msg) {
	//        int error = g.glGetError();
	//        int nerrors = 3;
	//        while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
	//            final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
	//            if (trace.length > 1) {
	//                final String className = trace[2].getClassName();
	//                final String methodName = trace[2].getMethodName();
	//                final int lineNumber = trace[2].getLineNumber();
	//                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg + " at "
	//                        + className + "." + methodName + " (line " + lineNumber + ")");
	//            } else {
	//                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg);
	//            }
	//            // Thread.dumpStack();
	//            error = g.glGetError();
	//        }
	//    }
	//    void clipPoint(final Point p) {
	//        if (p.x < 0) {
	//            p.x = 0;
	//        } else if (p.x > (getChip().getSizeX() - 1)) {
	//            p.x = getChip().getSizeX() - 1;
	//        }
	//        if (p.y < 0) {
	//            p.y = 0;
	//        } else if (p.y > (getChip().getSizeY() - 1)) {
	//            p.y = getChip().getSizeY() - 1;
	//        }
	//    }
	//    public Chip2D getChip() {
	//        return chip;
	//    }
	//
	//    public class Zoom {
	//
	//        final float zoomStepRatio = 1.3f;
	//        private Point startPoint = new Point();
	//        private Point endPoint = new Point();
	//        private Point centerPoint = new Point();
	//        float zoomFactor = 1;
	//        private boolean zoomEnabled = false;
	//        Point tmpPoint = new Point();
	//        double projectionLeft, projectionRight, projectionBottom, projectionTop; // projection rect points, computed on
	//        // zoom
	//
	//
	//
	//        public Point getStartPoint() {
	//            return startPoint;
	//        }
	//
	//        public void setStartPoint(final Point startPoint) {
	//            this.startPoint = startPoint;
	//        }
	//
	//        public Point getEndPoint() {
	//            return endPoint;
	//        }
	//
	//        public void setEndPoint(final Point endPoint) {
	//            this.endPoint = endPoint;
	//        }
	//
	//        private void unzoom() {
	//            setZoomEnabled(false);
	//            zoomFactor = 1;
	//            getZoom().setStartPoint(new Point(0, 0));
	//            final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
	//            centerPoint.setLocation(sx / 2, sy / 2);
	//            set3dOrigin(0, 0);
	//            // getZoom().setEndPoint(new Point(getChip().getSizeX(), getChip().getSizeY()));
	//            // if (!System.getProperty("os.name").contains("Mac")) {//crashes on mac os x 10.5
	//            // GL g = drawable.getGL();
	//            // g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
	//            // g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
	//            // g.glOrtho(-getBorderSpacePixels(), drawable.getWidth() + getBorderSpacePixels(), -getBorderSpacePixels(),
	//            // drawable.getHeight() + getBorderSpacePixels(), ZCLIP, -ZCLIP);
	//            // g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
	//            // }
	//        }
	//
	//        private void zoomcenter() {
	//            centerPoint = getMousePixel();
	//            setZoomEnabled(true);
	//        }
	//
	//        private void zoomin() {
	//            centerPoint = getMousePixel();
	//            zoomFactor *= zoomStepRatio;
	//            setZoomEnabled(true);
	//        }
	//
	//        private void zoomout() {
	//            centerPoint = getMousePixel();
	//            zoomFactor /= zoomStepRatio;
	//            setZoomEnabled(true);
	//        }
	//
	//        public boolean isZoomEnabled() {
	//            return zoomEnabled;
	//        }
	//
	//        public void setZoomEnabled(final boolean zoomEnabled) {
	//            this.zoomEnabled = zoomEnabled;
	//        }
	//    }
	//
	//    public void addGLEventListener(final GLEventListener listener) {
	//        System.out.println("addGLEventListener(" + listener + ")");
	//    }
	public void setPositionSecondCamera(FloatMatrix translate) {
		this.positionSecondCamera=translate;

	}
}//end of class
