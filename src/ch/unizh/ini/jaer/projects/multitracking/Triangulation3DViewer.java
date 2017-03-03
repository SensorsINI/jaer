package ch.unizh.ini.jaer.projects.multitracking;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

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
import com.jogamp.opengl.util.gl2.GLUT;

import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;

public class Triangulation3DViewer extends DisplayMethod implements GLEventListener,FrameAnnotater, Observer {
	public Triangulation3DViewer(ChipCanvas parent) {
		super(parent);

	}
	private GLU glu = new GLU();
	private int sizeOfWin=800;
	private int radius=10;
	public LinkedList<FloatMatrix> Xfinals=new LinkedList<FloatMatrix>();
	private boolean annotationEnabled=true;
	public GLCanvas glcanvas;


	   @Override
	   public void display( GLAutoDrawable drawable ) {
	      final GL2 gl = drawable.getGL().getGL2();
	      annotate(drawable);
	      gl.glTranslatef(this.sizeOfWin, 0.0f, this.sizeOfWin);
	        gl.glPushMatrix();
	        gl.glClearColor(0.1f, 0.1f, 0.1f, 0);
	        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

	        // rotate and align viewpoint
	        gl.glRotatef(getChipCanvas().getAngley(), 0, 1, 0); // rotate viewpoint by angle deg around the upvector
	        gl.glRotatef(getChipCanvas().getAnglex(), 1, 0, 0); // rotate viewpoint by angle deg around the upvector
	        gl.glTranslatef(getChipCanvas().getOrigin3dx(), getChipCanvas().getOrigin3dy(), 0);

	      //draw 3D axes
	      // First face
          gl.glColor3f(0, 0, 1);
          gl.glLineWidth(2.0f);
          gl.glBegin(GL.GL_LINE_LOOP);
          gl.glVertex3f(0, 0, 0);
          gl.glVertex3f(this.sizeOfWin, 0, 0);
          gl.glVertex3f(this.sizeOfWin, this.sizeOfWin, 0);
          gl.glVertex3f(0, this.sizeOfWin, 0);
          gl.glVertex3f(0, 0, 0);
          gl.glEnd();

          // Second face
          gl.glColor3f(1, 0, 0);
          gl.glLineWidth(2.0f);
          gl.glBegin(GL.GL_LINE_LOOP);
          gl.glVertex3f(0, 0, 0);
          gl.glVertex3f(0, 0, this.sizeOfWin);
          gl.glVertex3f(0, this.sizeOfWin, this.sizeOfWin);
          gl.glVertex3f(0, this.sizeOfWin, 0);
          gl.glVertex3f(0, 0, 0);
          gl.glEnd();

          // Floor
          gl.glBegin(GL2ES3.GL_QUADS);
          gl.glColor3f(0.2f, 0.2f, 0.2f);
          gl.glVertex3f(0.0f, 0.0f, 0.0f);
          gl.glVertex3f(0.0f, 0.0f, this.sizeOfWin);
          gl.glVertex3f(this.sizeOfWin, 0.0f, this.sizeOfWin);
          gl.glVertex3f(this.sizeOfWin, 0.0f, 0.0f);
          gl.glEnd();

	      if(Xfinals.size()!=0){
	        gl.glPushMatrix();
			gl.glColor3f(0, 0, 1);
			gl.glLineWidth(4);
			for(int i=0;i<Xfinals.size(); i++){
				//centerX[i]=Xfinals.get(i).get(0);
				//System.out.println(centerX);
				//centerY[i]=Xfinals.get(i).get(1);
				//centerZ[i]=Xfinals.get(i).get(2);

				gl.glBegin(GL.GL_LINE_LOOP);
				//System.out.println(centerY);

				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)+(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));

				gl.glEnd();
			}
	      }
	   }
	   public void setVectToDisplay(LinkedList<FloatMatrix> vect){
		   this.Xfinals=vect;
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

	        log.info("OpenGL implementation is: " + gl.getClass().getName() + "\nGL_VENDOR: "
	                + gl.glGetString(GL.GL_VENDOR) + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER) + "\nGL_VERSION: "
	                + gl.glGetString(GL.GL_VERSION) // + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
	        );
	        final float glVersion = Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0, 3));
	        if (glVersion < 1.3f) {
	            log.warning("\n\n*******************\nOpenGL version "
	                    + glVersion
	                    + " < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
	        }
	        // System.out.println("GLU_EXTENSIONS: "+glu.gluGetString(GLU.GLU_EXTENSIONS));

	        gl.setSwapInterval(1);
	        gl.glShadeModel(GLLightingFunc.GL_FLAT);

	        gl.glClearColor(0, 0, 0, 0f);
	        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
	        gl.glLoadIdentity();

	        gl.glRasterPos3f(0, 0, 0);
	        gl.glColor3f(1, 1, 1);
	        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "Initialized display");

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


	public void startNewWindows() {

	      //getting the capabilities object of GL2 profile
	      final GLProfile profile = GLProfile.get( GLProfile.GL2 );
	      GLCapabilities capabilities = new GLCapabilities(profile);

	      // The canvas
	      glcanvas = new GLCanvas( capabilities );
	      //Triangulation3DViewer triview = new Triangulation3DViewer();
	      glcanvas.addGLEventListener( this );
	      glcanvas.setSize( 800, 800 );

	      //creating frame
	      final JFrame frame = new JFrame ("triangulation 3D renderer");

	      //adding canvas to it
	      frame.getContentPane().add( glcanvas );
	      frame.setSize(frame.getContentPane().getPreferredSize() );
	      frame.setVisible( true );
	   }//end of main


	@Override
	public void update(Observable o, Object arg) {
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
	        gl.glPushMatrix();
			gl.glColor3f(0, 0, 1);
			gl.glLineWidth(4);
			for(int i=0;i<Xfinals.size(); i++){
				//centerX[i]=Xfinals.get(i).get(0);
				//System.out.println(centerX);
				//centerY[i]=Xfinals.get(i).get(1);
				//centerZ[i]=Xfinals.get(i).get(2);

				gl.glBegin(GL.GL_LINE_LOOP);
				//System.out.println(centerY);

				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)-(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)+(radius/2), Xfinals.get(i).get(2)+(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)-(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));
				gl.glVertex3f(Xfinals.get(i).get(0)+(radius/2), Xfinals.get(i).get(1)-(radius/2), Xfinals.get(i).get(2)+(radius/2));

				gl.glEnd();
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
	}//end of class
