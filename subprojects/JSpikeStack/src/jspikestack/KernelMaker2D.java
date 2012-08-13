/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.Dimension;
import javax.swing.JFrame;

/**
 *
 * @author Peter
 */
public class KernelMaker2D {
    
    public enum Types{Gaussian,MexiHat,Hori,Vert};    
    
    public interface Computable
    {
        float val(float i, float j);        
    }
    
    
    public static class MexiHat implements Computable
    {
        float mag1=2;
        float mag2=1;
        float majorWidth1=4;
        float minorWidth1=2;
        float majorWidth2=8;
        float minorWidth2=4;
        float angle;
        
        
        @Override
        public float val(float i, float j) {
            Gaussian g1=new Gaussian(mag1,majorWidth1,minorWidth1,angle);
            Gaussian g2=new Gaussian(mag2,majorWidth2,minorWidth2,angle);     
            return g1.val(i,j)-g2.val(i,j);
                    
        }
        
        
        
        
    }
    
    public static class Gaussian implements Computable
    {
        float mag=1;
        float majorWidth=4;
        float minorWidth=2;
        float angle=0;
        
        public Gaussian(){};
        
        
        public Gaussian(float magnitude,float width)
        {
            this(magnitude,width,width,0);
        }
        
        public Gaussian(float magnitude,float majWidth,float minWidth)
        {   this(magnitude,majWidth,minWidth,0);
        }
        
        public Gaussian(float magnitude,float majWidth,float minWidth,float ang){
            mag=magnitude;
            majorWidth=majWidth;
            minorWidth=minWidth;
            angle=ang;
        }

        @Override
        public float val(float x, float y) {
            
            double cosa=Math.cos(angle);
            double sin2a=Math.sin(2*angle);
            double sina=Math.sin(angle);
                        
            double a=cosa*cosa/(2*majorWidth*majorWidth)+sina*sina/(2*minorWidth*minorWidth);
            double b=-sin2a/(4*majorWidth*majorWidth)+sin2a/(4*minorWidth*minorWidth);
            double c=sina*sina/(2*majorWidth*majorWidth)+cosa*cosa/(2*minorWidth*minorWidth);
            
            return mag*((float)Math.exp(-(a*x*x+b*x*y+c*y*y)));
        }
               
        
    }
    
    public static float[][] makeKernel(Types t,int sizex,int sizey)
    {
        
        Computable comp;
        switch(t)
        {
            case Gaussian:
                comp=new Gaussian();
                break;
            case MexiHat:
                comp=new MexiHat();
                break;
            default:
                throw new UnsupportedOperationException("Type "+t.toString()+" not supported yet.");
        }
        
        return makeKernel(comp,sizex,sizey);
        
    }
    
    
    
    public static float[][] makeKernel(Computable comp,int sizex,int sizey)
    {
        
        float[][] kernel=new float[sizey][sizex] ;
        for (int i=0; i<sizey; i++)
            for (int j=0; j<sizex; j++)
            {   float y=i-sizey/2f+.5f;
                float x=j-sizex/2f+.5f;
                kernel[i][j]=comp.val(x,y);
            }
        
        return kernel;
    }
    
    static float[][] transpose(float[][] w)
    {
        float[][] wt=new float[w[0].length][w.length];
        for(int i=0; i<wt.length; i++) 
            for(int j=0; j<wt[i].length; j++)
                wt[i][j]=w[j][i];
        return wt;
    }
    
    public static float[][] neg(float[][] kern)
    {
        float[][] w=new float[kern.length][];
        for(int i=0; i<kern.length; i++) 
        {   w[i]=new float[kern[i].length];
            for(int j=0; j<kern[i].length; j++)
                w[i][j]=-kern[i][j];
        }
        return w;
        
    }
    
    public static void plot(float[][] w)
    {
        ImageDisplay disp=ImageDisplay.createOpenGLCanvas();
        
        float max=Float.NEGATIVE_INFINITY;
        float min=Float.POSITIVE_INFINITY;
        for (int i=0; i<w.length; i++)
            for (int j=0; j<w[i].length; j++)
            {   max=Math.max(max,w[i][j]);
                min=Math.min(min,w[i][j]);
            }
        
        max=Math.max(max,min+Float.MIN_VALUE);
        
        
        disp.setImageSize(w.length,w[0].length);
        
        disp.setSize(300,300);
//        disp.setPreferredSize(new Dimension(300,300));
        
        for (int i=0; i<w.length; i++)
            for (int j=0; j<w[i].length; j++)
                disp.setPixmapGray(j,i,(w[i][j]-min)/(max-min));    
        
            
        JFrame frm=new JFrame();
        frm.setSize(new Dimension(400,400));
        
        
        frm.getContentPane().add(disp);
        
        frm.setVisible(true);
        
        frm.repaint();
    }
    
    
    
}
