/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.Dimension;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;

/**
 * This class contains a number of static methods for creating and transforming 
 * 2D kernels, which can be useful as filters for image processing.
 * @author Peter
 */
public class KernelMaker2D {
    
    public enum Types{Gaussian,MexiHat,Hori,Vert};    
    
    public enum KernelClass {GAUSSIAN,MEXIHAT,DIFFGAUSSIAN,EXPONENTIAL,PARABOLA,FLAT,RANDOM};
    
    public enum Scaling {FitToRange,FitAboutZero}
    
    
    public static KernelPair[] kernPairs=new KernelPair[]{
        new KernelPair(KernelClass.GAUSSIAN,Gaussian.class),
        new KernelPair(KernelClass.MEXIHAT,MexiHat.class),
        new KernelPair(KernelClass.DIFFGAUSSIAN,DiffGaussian.class),    
        new KernelPair(KernelClass.PARABOLA,Parabola.class),    
        new KernelPair(KernelClass.FLAT,Flat.class),    
        new KernelPair(KernelClass.RANDOM,RandomKern.class),    
        new KernelPair(KernelClass.EXPONENTIAL,Exponential.class),  
    };
    
    
    public static class KernelPair
    {
        KernelClass type;
        Class<? extends Computable> kern;
        
        public KernelPair(KernelClass ktype,Class<? extends Computable> kernel)
        {   type=ktype;
            kern=kernel;
        }
      
        public Computable newInstance()
        {
            try {
                return (Computable) kern.newInstance();
            } catch (InstantiationException ex) {
                Logger.getLogger(KernelMaker2D.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } catch (IllegalAccessException ex) {
                Logger.getLogger(KernelMaker2D.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }
        
    }
    
    public static abstract class Computable extends Controllable implements Serializable
    {
        abstract float val(float i, float j);        
        
        public <T extends Computable> T copy()
        {
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            T copy = null;
            try {
                // write the object
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(baos);
                out.writeObject(this);
                out.flush();

                // read in the copy
                byte data[] = baos.toByteArray();
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                in = new ObjectInputStream(bais);
                copy = (T) in.readObject();
                
                out.close();
                in.close();
                
                
            }catch (IOException ex){
                ex.printStackTrace();
            
            }catch (ClassNotFoundException ex){
                ex.printStackTrace();
            
            } finally {
                
            }
            return copy;
            
        }
        
    }
    
    public static Computable newKernel(KernelClass arg)
    {        
        for(KernelPair k:kernPairs)
        {   if (k.type==arg)
                return k.newInstance();
        }
        throw new RuntimeException("Kernel type: "+arg.toString()+" is not paired with any Kernel class");    
        
    }
    
    public static KernelClass getKernelClass(Class<? extends Computable> kernClass)
    {
        for(KernelPair k:kernPairs)
        {   if (k.kern==kernClass)
                return k.type;
        }
        
        throw new RuntimeException("Kernel class: "+kernClass.getName()+" is not paired with any enum in KernelClass");    
    }
    
    
    public static class MexiHat  extends Computable
    {
        public float mag=2;
        public float ratio=2;
        public float majorWidth=2;              // How long is the surround gaussian?
        public float squeezeFactorCenter=1;     // How squeezed is center?
        public float ratioCenterSurround=2;     // How big is surround compared to center?
        public float squeezeFactorSurround=1;   // How squeezed is surround
        public float angle;
        
        
        @Override
        public float val(float i, float j) {
            
            
            
            Gaussian g1=new Gaussian(mag, majorWidth/ratioCenterSurround, squeezeFactorCenter, angle);
            Gaussian g2=new Gaussian(mag/ratio, majorWidth, squeezeFactorSurround, angle);     
            return g1.val(i,j)-g2.val(i,j);
                    
        }

        @Override
        public String getName() {
            return "Mexican Hat Kernel";
        }

        public float getMag() {
            return mag;
        }

        public void setMag(float mag) {
            this.mag = mag;
        }

        public float getRatio() {
            return ratio;
        }

        public void setRatio(float ratio) {
            this.ratio = ratio;
        }

        public float getMajorWidth() {
            return majorWidth;
        }

        public void setMajorWidth(float majorWidth) {
            this.majorWidth = majorWidth;
        }

        public float getSqueezeFactorCenter() {
            return squeezeFactorCenter;
        }

        public void setSqueezeFactorCenter(float squeezeFactorCenter) {
            this.squeezeFactorCenter = squeezeFactorCenter;
        }

        public float getRatioCenterSurround() {
            return ratioCenterSurround;
        }

        public void setRatioCenterSurround(float ratioCenterSurround) {
            this.ratioCenterSurround = ratioCenterSurround;
        }

        public float getSqueezeFactorSurround() {
            return squeezeFactorSurround;
        }

        public void setSqueezeFactorSurround(float squeezeFactorSurround) {
            this.squeezeFactorSurround = squeezeFactorSurround;
        }

        public float getAngle() {
            return angle;
        }

        public void setAngle(float angle) {
            this.angle = angle;
        }



    }
    
    /** Difference of two Gaussians.  The Gaussians are spaced apart by "minorWidth". */
    public static class DiffGaussian extends Computable
    {
        public float majorWidth=4;
        public float squeezeFactor=2;
        public float spaceFactor=1; // How widely spaced are the points
        public float angle=0;
        public float mag=1;

        @Override
        public String getName() {
            return "Difference-of-Gaussian Kernel";
        }

        @Override
        public float val(float i, float j) {
            
            float ang=(float)(angle*Math.PI/180);
            
            float minorWidth=majorWidth/squeezeFactor;
            
            float xoff=(float)-Math.sin(ang)*minorWidth*spaceFactor/2;
            float yoff=(float)Math.cos(ang)*minorWidth*spaceFactor/2;
            
            
            
            Gaussian g1=new Gaussian(mag, majorWidth, squeezeFactor,angle,xoff,yoff);  // Positive gaussian
            Gaussian g2=new Gaussian(mag, majorWidth, squeezeFactor,angle,-xoff,-yoff);  // Negative gaussian
            return g1.val(i,j)-g2.val(i,j);
            
//            return 1;
                    
        }

        // Getters/setters

        public float getMajorWidth() {
            return majorWidth;
        }

        public void setMajorWidth(float majorWidth) {
            this.majorWidth = majorWidth;
        }

        public float getSqueezeFactor() {
            return squeezeFactor;
        }

        public void setSqueezeFactor(float squeezeFactor) {
            this.squeezeFactor = squeezeFactor;
        }

        public float getSpaceFactor() {
            return spaceFactor;
        }

        public void setSpaceFactor(float spaceFactor) {
            this.spaceFactor = spaceFactor;
        }

        public float getAngle() {
            return angle;
        }

        public void setAngle(float angle) {
            this.angle = angle;
        }

        public float getMag() {
            return mag;
        }

        public void setMag(float mag) {
            this.mag = mag;
        }
        
    }
    
    
    public static class RandomKern extends Computable
    {
        public float mean;
        public float std;
        Random randy=new Random();

        @Override
        public String getName() {
            return "Random Kernel";
        }

        @Override
        public float val(float i, float j) {
            return (float)randy.nextGaussian()*std+mean;
        }

        
        public float getMean() {
            return mean;
        }

        public void setMean(float mean) {
            this.mean = mean;
        }

        public float getStd() {
            return std;
        }

        public void setStd(float std) {
            this.std = std;
        }
                
    }
    
    
    
    
    public static class Parabola extends Computable
    {
        public float width;
        public float mag=1; // Note that for now, mag is redundant, but may make it more intuitive to size the thing.

        @Override
        public float val(float i, float j) {
            float x=(i/getWidth());
            float y=(j/getWidth());
            return getMag()*(x*x+y*y);
        }

        @Override
        public String getName() {
            return "Parabolic Kernel";
        }

        public float getWidth() {
            return width;
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public float getMag() {
            return mag;
        }

        public void setMag(float mag) {
            this.mag = mag;
        }
    }
    
    
    
    public static class Gaussian extends Computable
    {
        public float mag=1;
        public float majorWidth=2;
        public float squeezeFactor=1;
        public float angle=0;
        public float xoffset=0;
        public float yoffset=0;
        
        public Gaussian(){};
        
        
        public Gaussian(float magnitude,float width)
        {
            this(magnitude,width,width,0);
        }
        
        public Gaussian(float magnitude,float majWidth,float minWidth)
        {   this(magnitude,majWidth,minWidth,0);
        }
        
        public Gaussian(float magnitude,float majWidth,float squeeze,float ang){
            mag=magnitude;
            majorWidth=majWidth;
            squeezeFactor=squeeze;
            angle=ang;
        }
        
        public Gaussian(float magnitude,float majWidth,float squeeze,float ang,float xoff,float yoff){
            this(magnitude,majWidth,squeeze,ang);
            xoffset=xoff;
            yoffset=yoff;
        }
        
        

        @Override
        public float val(float x, float y) {
            
            float ang=(float)(getAngle()*Math.PI/180);
            
            double cosa=Math.cos(ang);
            double sin2a=Math.sin(2*ang);
            double sina=Math.sin(ang);
                        
            
            float majWidth=majorWidth/2;
            float minorWidth=majWidth/squeezeFactor;
            
            
            double a=cosa*cosa/(2*majWidth*majWidth)+sina*sina/(2*minorWidth*minorWidth);
            double b=+sin2a/(4*majWidth*majWidth)-sin2a/(4*minorWidth*minorWidth);
            double c=sina*sina/(2*majWidth*majWidth)+cosa*cosa/(2*minorWidth*minorWidth);
            
            x-=xoffset;
            y-=yoffset;
            
            return getMag()*((float)Math.exp(-(a*x*x+2*b*x*y+c*y*y)));
        }

        @Override
        public String getName() {
            return "Gaussian Kernel";
        }

        public float getMag() {
            return mag;
        }

        public void setMag(float mag) {
            this.mag = mag;
        }

        public float getMajorWidth() {
            return majorWidth;
        }

        public void setMajorWidth(float majorWidth) {
            this.majorWidth = majorWidth;
        }

        public float getSqueezeFactor() {
            return squeezeFactor;
        }

        public void setSqueezeFactor(float squeezeFactor) {
            this.squeezeFactor = squeezeFactor;
        }

        public float getAngle() {
            return angle;
        }

        public void setAngle(float angle) {
            this.angle = angle;
        }

        public float getXoffset() {
            return xoffset;
        }

        public void setXoffset(float xoffset) {
            this.xoffset = xoffset;
        }

        public float getYoffset() {
            return yoffset;
        }

        public void setYoffset(float yoffset) {
            this.yoffset = yoffset;
        }

    }
    
    public static class Exponential extends Gaussian
    {
        @Override
        public float val(float x, float y) {
            
            float gauss=super.val(x,y);
            
            boolean pos=mag>0;
            
            gauss=(float)Math.sqrt(pos?gauss:-gauss);
            
            gauss*=Math.sqrt(Math.abs(mag));
            
            gauss=pos?gauss:-gauss;
            
            return  gauss;
            
        }
        
    }
    
    
    
    public static class Flat extends Computable
    {
        public float mag=1;
        
        public Flat(){};
        
        
        @Override
        public float val(float x, float y) {
            
            return mag;
        }

        @Override
        public String getName() {
            return "Gaussian Kernel";
        }

        public float getMag() {
            return mag;
        }

        public void setMag(float mag) {
            this.mag = mag;
        }

    }
    
    /** See "Resistive Grid Image Filtering: Input/Output Analysis via the CNN framework */
//    public static class RCgrid extends Computable
//    {
//        public float lambda;
//        public float cap;
//
//        @Override
//        float val(float i, float j) {
//            throw new UnsupportedOperationException("Not supported yet.");
//        }
//
//        @Override
//        public String getName() {
//            return "RC Grid";
//        }        
//    }
        
//    public static float[][] makeKernel(Types t,int sizex,int sizey)
//    {
//        
//        Computable comp;
//        switch(t)
//        {
//            case Gaussian:
//                comp=new Gaussian();
//                break;
//            case MexiHat:
//                comp=new MexiHat();
//                break;
//            default:
//                throw new UnsupportedOperationException("Type "+t.toString()+" not supported yet.");
//        }
//        
//        
//        
//        
//        return makeKernel(comp,sizex,sizey);
//        
//    }
        
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
    
    /** If a kernel has zeros along the edge, remove them (to save time) */
    public static float removeZeroEdges()
    {
        throw new UnsupportedOperationException("Not supported yet");
        
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
    
    /** Normalize kernel such that all elements sum to 1.  Warning, a balanced 
     kernel will cause +/- inf values
     */
    public static void normalizeKernel(float[][] kern)
    {
        float sum=0;
        for (int i=0; i<kern.length; i++)
            for (int j=0; j<kern[i].length; j++)
                sum+=kern[i][j];
                
        for (int i=0; i<kern.length; i++)
            for (int j=0; j<kern[i].length; j++)
                kern[i][j]/=sum;
        
        
    }
    
    /** Center the kernel so that all it's element values add to zero.
     */
    public static void centerKernel(float[][] kern)
    {
        int k=0;
        float mean=0;
        for (int i=0; i<kern.length; i++)
            for (int j=0; j<kern[i].length; j++)
            {    mean+=kern[i][j];
                k++;
            }
        
        mean/=k;
                
        for (int i=0; i<kern.length; i++)
            for (int j=0; j<kern[i].length; j++)
                kern[i][j]-=mean;
        
        
    }
    
    public static float[][] copy(float[][] kern)
    {
        float[][] w=new float[kern.length][];
        for(int i=0; i<kern.length; i++) 
        {   w[i]=new float[kern[i].length];
            System.arraycopy(kern[i], 0, w[i], 0, kern[i].length);
        }
        return w;
        
    }
    
    public static void scale(float[][] kern,float factor)
    {
        float[][] w=new float[kern.length][];
        for(int i=0; i<kern.length; i++) 
        {   w[i]=new float[kern[i].length];
            for(int j=0; j<kern[i].length; j++)
                kern[i][j]*=factor;
        }        
    }
    
    public static void plot(float[][] w)
    {
        plot(w,Scaling.FitAboutZero);
    }
    
    public static void plot(float[][] w,Scaling scaling)
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
        
        
        max=Math.abs(max);
        min=Math.abs(min);
        float absmax=Math.max(min,max);
        
        if (scaling==Scaling.FitAboutZero)
        {
            
            max=absmax;
            min=-absmax;
        }
        
        
        final DecimalFormat myFormatter = new DecimalFormat("0.###");
        
        disp.setImageSize(w[0].length,w.length);
        
        disp.setSize(300,300);
        
        disp.setTitleLabel("Range: [ "+myFormatter.format(min)+"   "+myFormatter.format(max)+" ] ");
        
//        disp.setPreferredSize(new Dimension(300,300));
        
        for (int i=0; i<w.length; i++)
            for (int j=0; j<w[i].length; j++)
            {   float val=w[i][j];
                if (val>0)
                    disp.setPixmapRGB(j, i, val/absmax, 0, 0);
                else
                    disp.setPixmapRGB(j, i, 0, 0, -val/absmax);
                
            }
//                disp.setPixmapGray(j,i,(w[i][j]-min)/(max-min));    
        
            
        JFrame frm=new JFrame();
        frm.setSize(new Dimension(400,400));
        
        
        frm.getContentPane().add(disp);
        
        frm.setVisible(true);
        
        frm.repaint();
    }
    
    public static class FloatConnection
    {   
        public float[][] weights;
        public int[][] targets;
        
        public FloatConnection(float[][] w,int[][] t)
        {weights=w; targets=t;};
    }
    
    public static class IntConnection
    {
        public int[][] weights;
        public int[][] targets;
        
        public IntConnection(int[][] w,int[][] t)
        {weights=w; targets=t;};
    }
    
    public static int[][] float2int(float[][] kern)
    {
        int[][] ker=allocateInts(getSizes(kern));
        for (int i=0; i<kern.length; i++)
            for (int j=0; j<kern.length; j++)
                ker[i][j]=(int)kern[i][j];
        return ker;
    }
    
    /** Given an 2d kernel, defining a convolution done by layer B on layer A, 
     * create the weight matrix defining the projections from A to B that will 
     * implement this convolution.  If A and B have different sizes, this is more
     * complicated than simply flipping the kernel.
     */
    public static FloatConnection invert(final float[][] kernel,int inDimx,int inDimy,int outDimx,int outDimy)
    {
        int[] kDims=getSizes(kernel);        
        int[] alloc=getAllocations(inDimx,inDimy,outDimx,outDimy,kDims);        
        final float[][] ww=allocateFloats(alloc);
        final int[][] targ=allocateInts(alloc);        
        final int[] preUnitOutputCount=new int[ww.length];
                
        Operation op=new Operation(){            
            @Override
            void run(int inputix, int outputix, int kernelj, int kernelk) {
                ww[inputix][preUnitOutputCount[inputix]]=kernel[kernelj][kernelk];
                targ[inputix][preUnitOutputCount[inputix]++]=outputix;
            }
        };         
        
        invert(op,inDimx,inDimy,outDimx,outDimy,kDims);
        
        return new FloatConnection(ww,targ);        
    }
    
    /** Same as floatConnection but for integer kernel */
    public static IntConnection invert(final int[][] kernel,int inDimx,int inDimy,int outDimx,int outDimy)
    {
        int[] kDims=getSizes(kernel);        
        int[] alloc=getAllocations(inDimx,inDimy,outDimx,outDimy,kDims);        
        final int[][] ww=allocateInts(alloc);
        final int[][] targ=allocateInts(alloc);        
        final int[] preUnitOutputCount=new int[ww.length];
                
        Operation op=new Operation(){            
            @Override
            void run(int inputix, int outputix, int kernelj, int kernelk) {
                ww[inputix][preUnitOutputCount[inputix]]=kernel[kernelj][kernelk];
                targ[inputix][preUnitOutputCount[inputix]++]=outputix;
            }
        };         
        
        invert(op,inDimx,inDimy,outDimx,outDimy,kDims);
        
        return new IntConnection(ww,targ);        
    }
    
    public static int[] getSizes(float[][] kern)
    {
        int[] sz=new int[kern.length];        
        for (int i=0; i<kern.length; i++)
            sz[i]=kern[i].length;        
        return sz;                
    }
    
    public static int[] getSizes(int[][] kern)
    {
        int[] sz=new int[kern.length];        
        for (int i=0; i<kern.length; i++)
            sz[i]=kern[i].length;        
        return sz;                
    }
        
    public static float[][] allocateFloats(int[] allocations)
    {
        float[][] f=new float[allocations.length][];        
        for(int i=0; i<f.length; i++)
            f[i]=new float[allocations[i]];        
        return f;
    }
    
    public static int[][] allocateInts(int[] allocations)
    {
        int[][] f=new int[allocations.length][];        
        for(int i=0; i<f.length; i++)
            f[i]=new int[allocations[i]];        
        return f;
    }
        
    /** Given an input layer size, output layer size, and kernel size, return a 
     * vector of how many units each element of the input layer must must 
     * project to.  This can be used in allocating output arrays. */
    static int[] getAllocations(final int inDimx,final int inDimy,final int outDimx,final int outDimy,final int[] kDims)
    {
        final int[] preUnitOutputCount=new int[inDimx*inDimy];
        
        Operation op=new Operation(){
            @Override
            void run(int inputix,int outputix,int kerneli,int kernelj) {
                preUnitOutputCount[inputix]++;
            }
        };
        
        
        // Step 1: Count how many outputs each input will send to.
        invert(op, inDimx,inDimy,outDimx,outDimy,kDims);
        return preUnitOutputCount;
    }
        
    static void invert(Operation op,final int inDimx,final int inDimy,final int outDimx,final int outDimy,final int[] kDims)
    {
        
        
        final int postUnits=outDimx*outDimy;
        final int preUnits=inDimx*inDimy;
        
//        final int[] preUnitOutputCount=new int[preUnits];
        
//        class IterateThrough
//        {
//            
//            
//            public void run(Operation op)
//            {
                // Step 1: go through all output units, find the associated input units, and find the number of outputs that each input will have

                for (int i=0; i<postUnits; i++)
                {

                    int odimx=i/outDimy;
                    int odimy=i%outDimy;

                    // Find the input around which this output should be centred
                    int idimx=(inDimx*odimx)/outDimx;            
                    int idimy=(inDimy*odimy)/outDimy;           

                    // Create target units
                    for (int j=0; j<kDims.length; j++)
                    {   for (int k=0; k<kDims[j]; k++)
                        {
                            // Location of input neuron of this synapse
                            int ix=idimx-kDims[j]/2+k;
                            int iy=idimy-kDims.length/2+j;

                            if (ix<0 || ix>=inDimx|| iy<0 || iy>=inDimy)
                                continue;

                            // Input neuron address
                            int index=iy+ix*inDimy;

//                            preUnitOutputCount[index]++;

                            op.run(index,i,j,k);

                        }
                    }
                }
//            }
//        }
            
//
//        IterateThrough it=new IterateThrough();

                
        

        // Step 2: Allocate arrays
//        ww=new float[preUnits][];
//        ttargets=new int[preLayer.nUnits()][];
//        for (int i=0; i<ww.length; i++)
//        {   ww[i]=new float[preUnitOutputCount[i]];
//            ttargets[i]=new int[preUnitOutputCount[i]];
//            preUnitOutputCount[i]=0;
//        }
//        op.allocate(preUnits, postUnits);

        // Step 3, fill in values            
//        it.run(op);
        
        
        
    }
        
//    public static mult
    
    
    abstract static class Operation
    {
        abstract void run(int inputix,int outputix,int kernelj,int kernelk);
    }
        
    
    /** Given a vector of input and output units, each output unit takes its state
     * as a weighted sum of the input vector units.
     * @param inVector the vector of inputs to sum from
     * @param weights a [outVector.length]x[inVector.length] weight matrix.
     * @param targets a [outVector.length]x[inVector.length] matrix of input addresses for each output unit.
     * @param outVector the Vector of outputs to write into.
     */
    public static void weightMult(float[] inVector,float[][] weights,int[][] targets,float[] outVector)
    {
        for (int i=0; i<outVector.length; i++)
        {
            float sum=0;
            
            for (int j=0; j<targets[i].length; j++)
                sum+=inVector[targets[i][j]]*weights[i][j];
            
            outVector[i]=sum;
        }
    }
    
    
    
    
}
