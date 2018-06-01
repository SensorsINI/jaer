/*
 * Copyright (C) 2018 Tobi Delbruck.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.jjslam;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Jean-Jaques Slotine SLAM without linearization
 *
 * @author Robin Deuber, Tobi Delbruck
 */
@Description("Jean-Jaques Slotine SLAM without linearization")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class JJSlam extends EventFilter2D implements FrameAnnotater{

    private float gainPropertional = getFloat("gainPropertional", 1f);
    private float cameraFocalLengthMm = getFloat("cameraFocalLengthMm", 8);
    private TextRenderer textRenderer = null;
    private CameraPose cameraPose = new CameraPose();
    private CameraAccRotVel cameraAccRotVel = new CameraAccRotVel();
    private WriteFile writeFile = new WriteFile("C:\\Users\\Robin\\polybox\\JJSLAM_Semester_Thesis\\asdfasdf.txt");
    private ImuMedianTracker tracker = null;


    public JJSlam(AEChip chip) {
        super(chip);
        setPropertyTooltip("gainPropertional", "feedback gain for reducing errrow");
        setPropertyTooltip("cameraFocalLengthMm", "lens focal length in mm");
        tracker = new ImuMedianTracker(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        tracker.filterPacket(in);
        Point2D.Float p = (Point2D.Float) tracker.getMedianPoint();
        float d = (float) Math.sqrt(p.x * p.x + p.y * p.y);

        return in;
    }

    private void updateState(ApsDvsEvent e) {
        
        //Update IMU measure object
        IMUSample imuSample = e.getImuSample();
        int t_current = imuSample.getTimestampUs();
        float accX = imuSample.getAccelX();
        float accY = -imuSample.getAccelY();
        float accZ = imuSample.getAccelZ();
        float yawRateDpsSample = -imuSample.getGyroYawY(); // update pan tilt roll state from IMU
        float tiltRateDpsSample = imuSample.getGyroTiltX();
        float rollRateDpsSample = imuSample.getGyroRollZ();
        //log.info(cameraAccRotVel.toString());
        cameraAccRotVel.setRotVel(tiltRateDpsSample, yawRateDpsSample, rollRateDpsSample);
        cameraAccRotVel.setAcc(accX, accY, accZ );
        cameraAccRotVel.setTimeUs(t_current);
        cameraPose.setOrientation(cameraAccRotVel.updateOrientation(cameraPose));
        //writeFile.writeToFile(cameraAccRotVel.toStringExport());
        
        //Calculate the new orientation
        
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        tracker.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int sy = chip.getSizeY(), sx = chip.getSizeX();
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        }
        textRenderer.beginRendering(sx, sy);
        gl.glColor4f(1, 1, 0, .7f);
        textRenderer.draw(cameraPose.toString(), 1, sy / 2);
        textRenderer.draw(cameraAccRotVel.toString(), 1, (sy/2-10));
        textRenderer.endRendering();
        
        //Draw the pitching orientation
        Point2D front = new Point2D.Float();
        front.setLocation(10+10*Math.sin(Math.toRadians(cameraPose.getOrientation()[1])), 200+10*Math.sin(Math.toRadians(cameraPose.getOrientation()[1])));
        Point2D back = new Point2D.Float();
        back.setLocation(10, 200);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2d(front.getX() - 10, front.getY());
        gl.glVertex2d(back.getX() + 10, back.getY());
        gl.glEnd();
    }

    /**
     * @return the gainPropertional
     */
    public float getGainPropertional() {
        return gainPropertional;
    }

    /**
     * @param gainPropertional the gainPropertional to set
     */
    public void setGainPropertional(float gainPropertional) {
        this.gainPropertional = gainPropertional;
        putFloat("gainPropertional", gainPropertional);
    }

    private class CameraPose {

        float[] x = new float[3]; 
        float[] u = new float[3];
        float[] phi = new float[3];
        Matrix3 phiRotMat = new Matrix3();
        

        @Override
        public String toString() {
            return String.format("CameraPose: [x,y,z]=[%.2f,%.2f,%.2f], [ux,uy,uz]=[%.2f,%.2f,%.2f]",
                    x[0], x[1], x[2],
                    phi[0], phi[1], phi[2]);
        }
        public void setOrientation( float[] a) {
            //Set the angle values
            phi[0]=a[0];
            phi[1]=a[1];
            phi[2]=a[2];
            //Set rotation matrix
            
            float[] b=new float[9];
            double alpha=Math.toRadians(phi[2]); //alpha belons to the z-axis
            double beta=Math.toRadians(phi[1]); //beta belongs to the y-axis
            double gamma=Math.toRadians(phi[0]); //beta belongs to the x-axis
            b[0]=(float)(Math.cos(alpha)*Math.cos(beta));
            b[1]=(float)((Math.cos(alpha)*Math.sin(beta)*Math.sin(gamma))-(Math.sin(alpha)*Math.cos(gamma)));
            b[2]=(float)((Math.cos(alpha)*Math.sin(beta)*Math.cos(gamma))+(Math.sin(alpha)*Math.sin(gamma)));
            b[3]=(float)(Math.sin(alpha)*Math.cos(beta));
            b[4]=(float)((Math.sin(alpha)*Math.sin(beta)*Math.sin(gamma))+(Math.cos(alpha)*Math.cos(gamma)));
            b[5]=(float)((Math.sin(alpha)*Math.sin(beta)*Math.cos(gamma))-(Math.cos(alpha)*Math.sin(gamma)));
            b[6]=(float)(-Math.sin(beta));
            b[7]=(float)(Math.cos(beta)*Math.sin(gamma));
            b[8]=(float)(Math.cos(beta)*Math.cos(gamma));
            phiRotMat.setValuesArray(b);  
        }
        public float[] getOrientation() {
            float[] a = new float[3];
            a[0]=phi[0];
            a[1]=phi[1];
            a[2]=phi[2];
            return a;
                    
        }
    }
    
    private class WriteFile {
        private String path;
        private boolean append_to_file = true;
        BufferedWriter writer = null;
    
        public WriteFile (String file_path) {
            path = file_path;
        }
        
        public WriteFile (String file_path, boolean append_value) {
            path = file_path;
            append_to_file=append_value;
        }
        
        public void writeToFile (String textline) {
            log.info(String.format("%b", append_to_file));
            
            
            try
            {
                writer = new BufferedWriter (new FileWriter(path, append_to_file));
                writer.write(textline);
                writer.newLine();
            }
            catch(Exception e)
                {
                }
            finally
            {   try
                {
                    if(writer != null)
                        writer.close();
                }
                catch(Exception e)
                    {
                    }
            }
        }
    }
            
            
            
    
    private class CameraAccRotVel {

        float[] acc = new float[3];
        float[] rotVel = new float[3];
        int timeUs_curr = 0;
        int timeUs_prev = 0;
        int dTUs=0;
        int test=0;
        
        @Override
        public String toString() {
            return String.format("Accelartion: [x,y,z]=[%.2f,%.2f,%.2f], [ux,uy,uz]=[%.2f,%.2f,%.2f], delta time = %d",
                    acc[0], acc[1], acc[2],
                    rotVel[0], rotVel[1], rotVel[2],test);
        }
        public String toStringExport(){
            return String.format("%f,%f,%f,%f,%f,%f,%d,%d,%d",acc[0], acc[1], acc[2],rotVel[0], rotVel[1], rotVel[2],timeUs_curr,timeUs_prev, dTUs);
        }
        public void setAcc (float a, float b, float c )
        {
            acc[0]=a+0.2531f;
            acc[1]=b+0.9709f;
            acc[2]=c-0.5161f;
        }
        public void setRotVel (float a, float b, float c)
        {
            rotVel[0]=a;
            rotVel[1]=b;
            rotVel[2]=c;       
        }
        public void setTimeUs (int time_current)
        {
            timeUs_prev = timeUs_curr;
            timeUs_curr = time_current;
            dTUs=timeUs_curr-timeUs_prev;
            test=test+1;
        }
        public float[] updateOrientation(CameraPose pose){
            float[] current_orientation = new float[3];
            if ((dTUs>0) && (dTUs<10000)){
                current_orientation[0]=pose.getOrientation()[0]+rotVel[0]*dTUs*0.000001f;
                current_orientation[1]=pose.getOrientation()[1]+rotVel[1]*dTUs*0.000001f;
                current_orientation[2]=pose.getOrientation()[2]+rotVel[2]*dTUs*0.000001f;
            }
            else {
                log.info("corrupted delte time - no update");
                current_orientation = pose.getOrientation();
            }
            return current_orientation;
        }
    }

    class ImuMedianTracker extends MedianTracker {

        public ImuMedianTracker(AEChip chip) {
            super(chip);
        }

        @Override
        public EventPacket filterPacket(EventPacket in) {
            if (!(in instanceof ApsDvsEventPacket)) {
                throw new RuntimeException("only works with Davis packets that have IMU data");
            }
            ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
            int n = in.getSize();

            lastts = in.getLastTimestamp();
            dt = lastts - prevlastts;
            prevlastts = lastts;

            int[] xs = new int[n], ys = new int[n];// big enough for all events, including IMU and APS events if there are those too
            int index = 0;
            Iterator itr = packet.fullIterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                ApsDvsEvent e = (ApsDvsEvent) itr.next();
                if (e.isImuSample()) {
                    processEvents(xs, ys, index);
                    index = 0;
                    updateState(e);
                } else if (e.isDVSEvent()) {
                    xs[index] = e.x;
                    ys[index] = e.y;
                    index++;
                }
            }
            return in;
        }

        private void processEvents(int[] xs, int[] ys, int count) {
            if (count < 1) {
                return;
            }
            Arrays.sort(xs, 0, count); // only sort up to index because that's all we saved
            Arrays.sort(ys, 0, count);
            float x, y;
            if (count % 2 != 0) { // odd number points, take middle one, e.g. n=3, take element 1
                x = xs[count / 2];
                y = ys[count / 2];
            } else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
                x = (float) (((float) xs[count / 2 - 1] + xs[count / 2]) / 2f);
                y = (float) (((float) ys[count / 2 - 1] + ys[count / 2]) / 2f);
            }
            xmedian = xFilter.filter(x, lastts);
            ymedian = yFilter.filter(y, lastts);
            int xsum = 0, ysum = 0;
            for (int i = 0; i < count; i++) {
                xsum += xs[i];
                ysum += ys[i];
            }
            float instantXmean = xsum / count;
            float instantYmean = ysum / count;
            float xvar = 0, yvar = 0;
            float tmp;
            for (int i = 0; i < count; i++) {
                tmp = xs[i] - instantXmean;
                tmp *= tmp;
                xvar += tmp;

                tmp = ys[i] - instantYmean;
                tmp *= tmp;
                yvar += tmp;
            }
            xvar /= count;
            yvar /= count;
            xstd = xStdFilter.filter((float) Math.sqrt(xvar), lastts);
            ystd = yStdFilter.filter((float) Math.sqrt(yvar), lastts);
            xmean = xMeanFilter.filter(instantXmean, lastts);
            ymean = yMeanFilter.filter(instantYmean, lastts);
            medianPoint.setLocation(xmedian, ymedian);
            meanPoint.setLocation(instantXmean, instantYmean);
            stdPoint.setLocation(xstd * numStdDevsForBoundingBox, ystd * numStdDevsForBoundingBox);
        }

    }

    /**
     * @return the cameraFocalLengthMm
     */
    public float getCameraFocalLengthMm() {
        return cameraFocalLengthMm;
    }

    /**
     * @param cameraFocalLengthMm the cameraFocalLengthMm to set
     */
    public void setCameraFocalLengthMm(float cameraFocalLengthMm) {
        this.cameraFocalLengthMm = cameraFocalLengthMm;
        putFloat("cameraFocalLengthMm", cameraFocalLengthMm);
    }
    
    private class Matrix3 {
        float e11;
        float e12;
        float e13;
        float e21;
        float e22;
        float e23;
        float e31;
        float e32;
        float e33;
        
        
        //Fill colums first
        public void setValuesIndividual (float a, float b, float c, float d, float e, float f,float g,float h,float i){
            e11=a;
            e12=b;
            e13=c;
            e21=d;
            e22=e;
            e23=f;
            e31=g;
            e32=h;
            e33=i;
        }
        public void setValuesArray (float[] a){
            e11=a[0];
            e12=a[1];
            e13=a[2];
            e21=a[3];
            e22=a[4];
            e23=a[5];
            e31=a[6];
            e32=a[7];
            e33=a[8];
        }
        public float[] getArray(){
            float[] a=new float[9];
            a[0]=e11;
            a[1]=e12;
            a[2]=e13;
            a[3]=e21;
            a[4]=e22;
            a[5]=e23;
            a[6]=e31;
            a[7]=e32;
            a[8]=e33;  
            return a; 
        }
        public float[] getTransposedArray (){
            float[] a=new float[9];
            a[0]=e11;
            a[3]=e12;
            a[6]=e13;
            a[1]=e21;
            a[4]=e22;
            a[7]=e23;
            a[2]=e31;
            a[5]=e32;
            a[8]=e33;  
            return a; 
        }
        public float[] matrixTimesVector(float [] v){
            float[] a=new float[3];
            a[0]=e11*v[0]+e12*v[1]+e13*v[2];
            a[1]=e21*v[0]+e22*v[1]+e23*v[2];
            a[2]=e31*v[0]+e32*v[1]+e33*v[3];
            return a;
        }
    }
}
