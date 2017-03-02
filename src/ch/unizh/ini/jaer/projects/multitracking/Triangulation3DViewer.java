package ch.unizh.ini.jaer.projects.multitracking;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import org.jblas.FloatMatrix;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.graphics.FrameAnnotater;

public class Triangulation3DViewer implements GLEventListener,FrameAnnotater, Observer {
	private GLU glu = new GLU();
	private int radius=10;
	private LinkedList<FloatMatrix> Xfinals=new LinkedList<FloatMatrix>();

	   @Override

	   public void display( GLAutoDrawable drawable ) {
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
	   public void setVectToDisplay(LinkedList<FloatMatrix> vect){
		   this.Xfinals=vect;
	   }
	   @Override
	   public void dispose( GLAutoDrawable arg0 ) {
	      //method body
	   }

	   @Override
	   public void init( GLAutoDrawable arg0 ) {
	      // method body
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
	      final GLCanvas glcanvas = new GLCanvas( capabilities );
	      Triangulation3DViewer triview = new Triangulation3DViewer();
	      glcanvas.addGLEventListener( triview );
	      glcanvas.setSize( 400, 400 );

	      //creating frame
	      final JFrame frame = new JFrame (" 3d line");

	      //adding canvas to it
	      frame.getContentPane().add( glcanvas );
	      frame.setSize(frame.getContentPane().getPreferredSize() );
	      frame.setVisible( true );
	   }//end of main


	@Override
	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub

	}
	@Override
	public void setAnnotationEnabled(boolean yes) {
		// TODO Auto-generated method stub

	}
	@Override
	public boolean isAnnotationEnabled() {
		// TODO Auto-generated method stub
		return false;
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

	}//end of class
