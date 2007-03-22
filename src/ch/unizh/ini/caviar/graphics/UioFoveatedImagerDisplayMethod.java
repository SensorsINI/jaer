/*
 * UioFoveatedImagerDisplayMethod.java
 *
 * Created on 12. mai 2006, 13:00
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.graphics;

// import ch.unizh.ini.caviar.graphics.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
/**
 * This DisplayMethod draws a foveated image with higher density of pixels in
 * the center than in the periphery. Periphery pixels are 'adaptive' (i.e. 
 * motion-sensitive. The DisplayMethod is particular to the UioFoveatedImager
 * and it contains some globals so it needs rewriting to be used by other foveated
 * cameras. 
 *
 * Future plans (TODO's):
 * Optimize the main for-loops so no isInFovea() test is done.
 *
 * @author hansbe@ifi.uio.no
 */
public class UioFoveatedImagerDisplayMethod extends DisplayMethod implements DisplayMethod2D {
    private int startX;
    private int endX;
    private int startY;
    private int endY;
    
    /** Creates a new instance of UioFoveatedImagerDisplayMethod */
    public UioFoveatedImagerDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
        // marks out a fovea rectangle
        // surrounding pixels are 4x the size
        startX=8; endX = 83-8;
        startY=8; endY = 87-9;
        
    }
    
        public void display(GLAutoDrawable drawable){
        GL gl=setupGL(drawable);
        AEChipRenderer renderer=(AEChipRenderer)(chipCanvas.renderer);
        // renderer.grayValue = 0f; //grayquest
        float[][][] fr = renderer.getFr();        
        if (fr == null){
            return;
        }
        
        
        float gray = renderer.getGrayValue();
        // gl.glClearColor(gray,gray,gray,0f);
        gl.glClearColor(0f,0f,0f,0.0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

        try{
            //        for(int i=0;i<fr.length;i++){
            //            for(int j=0;j<fr[i].length;j++){
            
            // now iterate over the frame (fr)
            for (int x = chipCanvas.zoom.getStartPoint().x; x < chipCanvas.zoom.getEndPoint().x; x++){
                for (int y = chipCanvas.zoom.getStartPoint().y; y < chipCanvas.zoom.getEndPoint().y; y++){
                    
                    float[] f = fr[y][x];
                    if(f[0]==gray && f[1]==gray && f[2]==gray) continue;
                    
                    if (!isInFovea(x, y)) {
                        float sx, sy, ex, ey;
                        // 42 pixels surround the fovea on top and bottom
                        // 44 pixels surround the fovea on left and right
                        // 83 total x pixels
                        // 87 total y pixels
//                        if(f[0]==gray && f[1]==gray && f[2]==gray) 
//                            {gl.glColor3f(0f,0f,0f);}
//                        else                                                
                        sx = (float) ((float)x/83f)*41f*2f-0.5f;
                        ex = (float) ((float)(x+2)/83f)*41f*2f-0.5f;
                        sy = (float) ((float)y/87f)*43f*2f-0.5f;
                        ey = (float) ((float)(y+2)/87f)*43f*2f-0.5f;
                        //if(incy==0) incy=incx;
                        //if(incx==0) incx=incy; 
                        gl.glColor3f(1f,1f,1f);
                        gl.glRectf(sx,sy,ex,ey);
                        fr[y][x][0]=0;
                        fr[y][x][1]=0;
                        fr[y][x][2]=0;
                        //this.renderer.playSpike(0);
                    }
                    else
                    {
//                    int x = i,  y = j; // dont flip y direction because retina coordinates are from botton to top (in user space, after lens) are the same as default OpenGL coordinates
                    int fixY = y;
                    if (y==startY) fixY++;
                    //gl.glColor3f(0.9f-1.6f*f[0],0.9f-1.6f*f[1],0.9f-1.6f*f[2]);                    
                    gl.glColor3f(f[0],f[0],f[0]);
                    gl.glRectf(x-.5f,fixY-1f, x+.5f, fixY);
                    }
                }
            }
            
            
            
        }catch(ArrayIndexOutOfBoundsException e){
            log.warning("while drawing frame buffer");
            e.printStackTrace();
            chipCanvas.unzoom(); // in case it was some other chip had set the zoom
            gl.glPopMatrix();
        }
        
        // outline frame
        gl.glColor4f(0f,1f,0f,0f);
        gl.glLineWidth(2f);
        gl.glBegin(GL.GL_LINE_LOOP);
        final float o = .5f;
        final float w = renderer.getChip().getSizeX()-1;
        final float h = renderer.getChip().getSizeY()-1;
        gl.glVertex2f(-o,-o);
        gl.glVertex2f(w+o,-o);
        gl.glVertex2f(w+o,h+o);
        gl.glVertex2f(-o,h+o);
        gl.glEnd();
        
        
// following are apparently not needed, this happens anyhow before buffer swap
//        gl.glFlush();
//        gl.glFinish();
        
    }
        
    private boolean isInFovea(int aex, int aey){
        if(aex>endX) return false;
        if(aex<startX) return false;       
        if(aey>endY) return false;
        if(aey<startY) return false;
        return true;    
    }
}
