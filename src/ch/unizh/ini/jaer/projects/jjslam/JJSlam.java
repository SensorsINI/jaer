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
    private int i=0;
    private float sum_i_x=0;
    private float sum_i_y=0;
    private float sum_i_z=0;
    private int tempCurrentTime=0;
    private float xRotVelInitalSum=0;
    private float yRotVelInitalSum=0;
    private float zRotVelInitalSum=0;
    private float gainPropertional = getFloat("gainPropertional", 1f);
    private float cameraFocalLengthMm = getFloat("cameraFocalLengthMm", 8);
    private TextRenderer textRenderer = null;
    private CameraPose cameraPose = new CameraPose();
    private CameraAccRotVel cameraAccRotVel = new CameraAccRotVel();
    private WriteFile writeFile = new WriteFile("C:\\Users\\Robin\\polybox\\JJSLAM_Semester_Thesis\\asdfasdf.txt");
    private ImuMedianTracker tracker = null;
    private landmark landmark1 = new landmark();
    private float tempValueX=0;
    private float tempValueY=0;
    private float tempValueZ=0;


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
        float accY = imuSample.getAccelY();
        float accZ = imuSample.getAccelZ();
        float yawRateDpsSample =  imuSample.getGyroYawY();
        float tiltRateDpsSample = imuSample.getGyroTiltX();
        float rollRateDpsSample = imuSample.getGyroRollZ();
        //log.info(cameraAccRotVel.toString());
        cameraAccRotVel.setRotVel(tiltRateDpsSample, yawRateDpsSample, rollRateDpsSample);
        cameraAccRotVel.setAcc(accX, accY, accZ );
        cameraAccRotVel.setTimeUs(t_current);
        cameraPose.setTimeUs(t_current);
        float[] orientation=cameraAccRotVel.updateOrientation(cameraPose);
        cameraPose.setOrientation(orientation);
        float[] velocity=cameraAccRotVel.updateVelocityWorld(cameraPose);
        cameraPose.setVelocity(velocity);
        i=i+1;
        tempCurrentTime=t_current;
        //landmark1.estimatePose(cameraAccRotVel, cameraPose);
    
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
        textRenderer.draw(String.format("%d %d",i, tempCurrentTime), 1 ,(sy / 2+80));
        textRenderer.draw(cameraPose.toStringRotMatLine1(),1, (sy / 2+60));
        textRenderer.draw(cameraPose.toStringRotMatLine2(),1, (sy / 2+40));
        textRenderer.draw(cameraPose.toStringRotMatLine3(),1, (sy / 2+20));
        textRenderer.draw(cameraPose.toStringOrientation(), 1, sy / 2);
        textRenderer.draw(cameraPose.toStringVelocity(),1, (sy / 2-20));
        textRenderer.draw(cameraAccRotVel.toStringAccelerationBody(),1, (sy / 2-40));
        textRenderer.draw(cameraAccRotVel.toStringAccelerationWorld(),1, (sy / 2-60));
        textRenderer.draw(cameraAccRotVel.toStringRotVelRaw(),1, (sy/2 -80));
        textRenderer.draw(String.format("%f %f %f",tempValueX,tempValueY,tempValueZ), 1 ,(sy/2-100));
        textRenderer.endRendering();
        
        
        /*
        //Draw the pitching orientation
        Point2D front = new Point2D.Float();
        front.setLocation(10+10*Math.sin(Math.toRadians(cameraPose.getOrientation()[1])), 200+10*Math.sin(Math.toRadians(cameraPose.getOrientation()[1])));
        Point2D back = new Point2D.Float();
        back.setLocation(10, 200);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex2d(front.getX() - 10, front.getY());
        gl.glVertex2d(back.getX() + 10, back.getY());
        gl.glEnd
        */
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

        int t_current;
        float[] x = new float[3]; 
        float[] u = new float[3];
        float[] phi = new float[3];
        Matrix3 phiRotMat = new Matrix3();
        

       
        public String toStringOrientation() {
            return String.format("CameraPose: [orientation]=[%.2f,%.2f,%.2f]",
                    phi[0], phi[1], phi[2]);
        }
        public String toStringVelocity(){
            return String.format("CameraVelocity: [ux,uy,uz]=[%.2f,%.2f,%.2f]", 
                    u[0],u[1], u[2]);
        }
        public String toStringRotMatLine1 () {
            return String.format("RotMat Line 1: %.2f %.2f %.2f",
                    phiRotMat.getArray()[0], phiRotMat.getArray()[1], phiRotMat.getArray()[2]);
        }
        public String toStringRotMatLine2 () {
            return String.format("RotMat Line 2: %.2f %.2f %.2f",
                    phiRotMat.getArray()[3], phiRotMat.getArray()[4], phiRotMat.getArray()[5]);
        }
        public String toStringRotMatLine3 () {
            return String.format("RotMat Line 3: %.2f %.2f %.2f",
                    phiRotMat.getArray()[6], phiRotMat.getArray()[7], phiRotMat.getArray()[8]);
        }
        public void setOrientation( float[] a) {
            //Set the angle values
            
            phi[0]=a[0];
            phi[1]=a[1];
            phi[2]=a[2];
           
            
            //Set rotation matrix
            //This transformation matrix takes a vector from the body frame (which
            //is rotated with alpha, beta, gamma) and gives back its representation
            //in the fixed world frame. 
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
        public void setVelocity (float[] a){
            u[0]=a[0];
            u[1]=a[1];
            u[2]=a[2];
        }
        public void setTimeUs(int a){
            t_current=a;
        }
        public float[] getOrientation() {
            float[] a = new float[3];
            a[0]=phi[0];
            a[1]=phi[1];
            a[2]=phi[2];
            return a;
                    
        }
        public float[] getVelocity(){
            float[] a = new float[3];
            a[0]=u[0];
            a[1]=u[1];
            a[2]=u[2];
            return a;
        }
        public Matrix3 getPhiRotMa(){
            return phiRotMat;
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
            
    private class landmark {
        private Matrix3 P = new Matrix3();
        private Matrix3 Q = new Matrix3();
        private Matrix3 Omega = new Matrix3();
        private Matrix3 HRH =new Matrix3();
        //Rinv and H are filled as follow: H[0]=h11, H[1]=h12,...
        float Rinv[]={0, 0, 0, 0};
        float H[]= new float[6];
        
        public void estimatePose(CameraAccRotVel AccRotVel, CameraPose cameraPose) {
            //Set the constants
            float valuesQ[]={0,0,0,0,0,0,0,0,0};
            Q.setValuesArray(valuesQ);
            //Change the directions of the linear velocity
            float velocityWorld[]=cameraPose.getVelocity();
            float velocityJJ[] = new float[3];
            velocityJJ[0]=velocityWorld[0];
            velocityJJ[1]=-velocityWorld[1];
            velocityJJ[2]=velocityWorld[2];
            //Change the values of the rotational velocity
            float rotVelBody[]=AccRotVel.getRotVel();
            float rotVelJJ[] = new float[3];
            rotVelJJ[0]=rotVelBody[0];
            rotVelJJ[1]=-rotVelBody[1];
            rotVelJJ[2]=rotVelBody[2];
            //Update the omega matrix (Corrdinate System!)
            Omega.setValuesIndividual(0f, -rotVelJJ[2], rotVelJJ[1], rotVelJJ[2], 0f, -rotVelJJ[0], -rotVelJJ[1], rotVelJJ[0], 0f);
            //Fill H with the information of the tracker (different coordinate System)
            
            //Caclate HRH
            float hrhvalues[]=new float[9];
            hrhvalues[0]=H[0]*H[0]*Rinv[0];
            hrhvalues[1]=H[0]*H[4]*Rinv[1];
            hrhvalues[2]=H[0]*H[2]*Rinv[0]+H[5]*H[0]*Rinv[1];
            hrhvalues[3]=H[4]*H[0]*Rinv[2];
            hrhvalues[4]=H[4]*H[4]*Rinv[3];
            hrhvalues[5]=H[4]*H[2]*Rinv[2]+H[5]*H[4]*Rinv[3];
            hrhvalues[6]=H[0]*H[2]*Rinv[0]+H[0]*H[6]*Rinv[2];
            hrhvalues[7]=H[4]*H[2]*Rinv[1]+H[4]*H[6]*Rinv[3];
            hrhvalues[8]=H[2]*H[2]*Rinv[0]+H[2]*H[5]*Rinv[1]+H[6]*H[2]*Rinv[2]+H[6]*H[5]*Rinv[3];
            HRH.setValuesArray(hrhvalues);
            //Covariance Update
            
            
            
            //Next Step is to update the postition estimation
           
            
            //Calculate x in the normal frame and use it as return value
            
            
            
        }
        
    }        
            
    
    private class CameraAccRotVel {

        float[] accBody = new float[3];
        float[] accWorld = new float[3];
        float[] rotVelBody = new float[3];
        int timeUs_curr = 0;
        int timeUs_prev = 0;
        int dTUs=0;
        int test=0;
        
        //Variables for storing previous values for the orientation averaging filters
        float[] buffer_x_orientation_complementary = new float[200];
        float[] buffer_z_orientation_complementary = new float[200];
        float previous_x_orientation_complementary=0;
        float previous_z_orientation_complementary=0;
        
        //Variables for storing the values of the acceleration averaging filters
        int filterSizeAcc=3000;
        int filterSizeVel=3000;
        float[] xAccWorldBuffer = new float[filterSizeAcc];
        float[] yAccWorldBuffer = new float[filterSizeAcc];
        float[] zAccWorldBuffer = new float[filterSizeAcc];
        float[] AccWorldPrevious = new float[3];
        float[] AccWorldConstBias = new float[3];
        
        //Variable for storing the values of the velocity averaging filters
        float[] xVelWorldBuffer = new float[filterSizeVel];
        float[] yVelWorldBuffer = new float[filterSizeVel];
        float[] zVelWorldBuffer = new float[filterSizeVel];
        float[] VelWorldPrevious = new float[3];
        float[] VelWorldConstBias = new float[3];
  
        public String toStringAccelerationBody() {
            return String.format("Accelartion Body: [x,y,z]=[%.2f,%.2f,%.2f]",
                    accBody[0], accBody[1], accBody[2]);
        }
        public String toStringAccelerationWorld() {
            return String.format("Accelartion World: [x,y,z]=[%.2f,%.2f,%.2f]",
                    accWorld[0], accWorld[1], accWorld[2]);
        }
        public String toStringRotVelRaw() {
            return String.format("RotVel: [x,y,z]=[%.2f,%.2f,%.2f]",
                    rotVelBody[0], rotVelBody[1], rotVelBody[2]);
        }
        public String toStringExport(){
            return String.format("%f,%f,%f,%f,%f,%f,%d,%d,%d",accBody[0], accBody[1], accBody[2],rotVelBody[0], rotVelBody[1], rotVelBody[2],timeUs_curr,timeUs_prev, dTUs);
        }
        public void setAcc (float a, float b, float c )
        {
            accBody[0]=-a;
            accBody[1]=-b;
            accBody[2]=c;
        }
        public void setRotVel (float a, float b, float c)
        {
            /*IMU Test
            rotVelBody[0]=360f*(a+0.2686f);
            rotVelBody[1]=360f*(b+1.0409f);
            rotVelBody[2]=360f*(c-0.4866f);
            */
            //All together
            rotVelBody[0]=a;
            rotVelBody[1]=b;
            rotVelBody[2]=c;
        }
        public float[] getRotVel (){
            float[] a = new float[3];
            a[0]=rotVelBody[0];
            a[1]=rotVelBody[1];
            a[2]=rotVelBody[2];
            return a;
        }
        public void setTimeUs (int time_current)
        {
            timeUs_prev = timeUs_curr;
            timeUs_curr = time_current;
            dTUs=timeUs_curr-timeUs_prev;
            test=test+1;
        }
        public float[] updateOrientation(CameraPose pose){
            float[] current_orientation_integration = new float[3];
            float[] fused_orientation_integration = new float[3];
            float current_x_orientation_complementary=0;
            float current_z_orientation_complementary=0;
            float filtered_x_orientation_complementary=0;
            float filtered_z_orientation_complementary=0;
            float movAvSumX=0;
            float movAvSumZ=0;
            
            
            if ((dTUs>0) && (dTUs<100000)){
                current_orientation_integration[0]=pose.getOrientation()[0]+(rotVelBody[0]-(xRotVelInitalSum/3000f))*(360f/344f)*dTUs*0.000001f;
                current_orientation_integration[1]=pose.getOrientation()[1]+(rotVelBody[1]-(yRotVelInitalSum/3000f))*(360f/348f)*dTUs*0.000001f;
                current_orientation_integration[2]=pose.getOrientation()[2]+(rotVelBody[2]-(zRotVelInitalSum/3000f))*(360f/323f)*dTUs*0.000001f;
                
                
                
                current_x_orientation_complementary=(float)-Math.atan2((accBody[2]), (Math.signum(accBody[1])*(Math.sqrt((accBody[0]*accBody[0])+(accBody[1]*accBody[1])))));
                current_z_orientation_complementary=(float)-Math.atan2((-accBody[0]),accBody[1]);
               
                
                if(i<200)
                {
                    buffer_x_orientation_complementary[i]=current_x_orientation_complementary;
                    buffer_z_orientation_complementary[i]=current_z_orientation_complementary;
                    for (int j=0; j<(i+1); j++){
                        movAvSumX=movAvSumX+buffer_x_orientation_complementary[j];
                        movAvSumZ=movAvSumZ+buffer_z_orientation_complementary[j];
                    }
                    filtered_x_orientation_complementary=movAvSumX/((float)(i+1));
                    filtered_z_orientation_complementary=movAvSumZ/((float)(i+1));
                }
                else
                {
                    filtered_x_orientation_complementary=previous_x_orientation_complementary+((current_x_orientation_complementary-buffer_x_orientation_complementary[0])/200f);
                    filtered_z_orientation_complementary=previous_z_orientation_complementary+((current_z_orientation_complementary-buffer_z_orientation_complementary[0])/200f);
                    for(int j=0; j<199;j++){
                        buffer_x_orientation_complementary[j]=buffer_x_orientation_complementary[j+1];
                        buffer_z_orientation_complementary[j]=buffer_z_orientation_complementary[j+1];
                    }
                    buffer_x_orientation_complementary[199]=current_x_orientation_complementary;
                    buffer_z_orientation_complementary[199]=current_z_orientation_complementary;
                }
                
                previous_x_orientation_complementary=filtered_x_orientation_complementary;
                previous_z_orientation_complementary=filtered_z_orientation_complementary;
                
                fused_orientation_integration[0]=0.5f*(filtered_x_orientation_complementary*180.0f/((float)(Math.PI))-4.91f)+0.5f*current_orientation_integration[0];
                fused_orientation_integration[1]=current_orientation_integration[1];
                fused_orientation_integration[2]=0.5f*(filtered_z_orientation_complementary*180.0f/((float)(Math.PI))+0.4863f)+0.5f*current_orientation_integration[2];
                
            }
            
            else {
                log.info("corrupted delta time - no orientation update");
                fused_orientation_integration = pose.getOrientation();
            }
            
            if(i<3000){
                xRotVelInitalSum=xRotVelInitalSum+rotVelBody[0];
                yRotVelInitalSum=yRotVelInitalSum+rotVelBody[1];
                zRotVelInitalSum=zRotVelInitalSum+rotVelBody[2];
                fused_orientation_integration=pose.getOrientation();
                log.info("Estimating constant bias of rotational velocity");
            }
            return fused_orientation_integration;
        }
        public float[] updateVelocityWorld(CameraPose pose){
            float[] velocityWorld = new float[3];
            Matrix3 phiRotMat=pose.getPhiRotMa();
            float[] AccWorldFiltered = new float[3];
            float[] VelWorldFiltered = new float[3];
            
            if ((dTUs>0) && (dTUs<100000)){
                
                accWorld=phiRotMat.matrixTimesVector(accBody);
                //Subract the constant offset of gravity
                accWorld[0]=accWorld[0];
                accWorld[1]=accWorld[1]-1f;
                accWorld[2]=accWorld[2];

                if(i<(filterSizeAcc)){
                    xAccWorldBuffer[i]=accWorld[0];
                    yAccWorldBuffer[i]=accWorld[1];
                    zAccWorldBuffer[i]=accWorld[2];
                    
                    AccWorldConstBias[0]=accWorld[0]/((float)filterSizeAcc)+AccWorldConstBias[0];
                    AccWorldConstBias[1]=accWorld[1]/((float)filterSizeAcc)+AccWorldConstBias[1];
                    AccWorldConstBias[2]=accWorld[2]/((float)filterSizeAcc)+AccWorldConstBias[2];
                    
                    AccWorldPrevious[0]=AccWorldConstBias[0];
                    AccWorldPrevious[1]=AccWorldConstBias[1];
                    AccWorldPrevious[2]=AccWorldConstBias[2];
                    
                    velocityWorld=pose.getVelocity();
                }
                else{
                    AccWorldFiltered[0]=AccWorldPrevious[0]+(accWorld[0]-xAccWorldBuffer[0])/((float)filterSizeAcc);
                    AccWorldFiltered[1]=AccWorldPrevious[1]+(accWorld[1]-yAccWorldBuffer[0])/((float)filterSizeAcc);
                    AccWorldFiltered[2]=AccWorldPrevious[2]+(accWorld[2]-zAccWorldBuffer[0])/((float)filterSizeAcc);
                    
                    AccWorldPrevious[0]=AccWorldFiltered[0];
                    AccWorldPrevious[1]=AccWorldFiltered[1];
                    AccWorldPrevious[2]=AccWorldFiltered[2];
                    
                    for(int j=0; j<(filterSizeAcc-1);j++){
                        xAccWorldBuffer[j]=xAccWorldBuffer[j+1];
                        yAccWorldBuffer[j]=yAccWorldBuffer[j+1];
                        zAccWorldBuffer[j]=zAccWorldBuffer[j+1];
                    }
                    xAccWorldBuffer[filterSizeAcc-1]=accWorld[0];
                    yAccWorldBuffer[filterSizeAcc-1]=accWorld[1];
                    zAccWorldBuffer[filterSizeAcc-1]=accWorld[2];
                    //Integrate the values
                    
                    velocityWorld[0]=pose.getVelocity()[0]+(accWorld[0]-AccWorldFiltered[0])*dTUs*9.81f*0.000001f;
                    velocityWorld[1]=pose.getVelocity()[1]+(accWorld[1]-AccWorldFiltered[1])*dTUs*9.81f*0.000001f;
                    velocityWorld[2]=pose.getVelocity()[2]+(accWorld[2]-AccWorldFiltered[2])*dTUs*9.81f*0.000001f; 
                  
                }
                
                //Do the velocity bias estimation
                if(i<(filterSizeVel)){
                    xVelWorldBuffer[i]=velocityWorld[0];
                    yVelWorldBuffer[i]=velocityWorld[1];
                    zVelWorldBuffer[i]=velocityWorld[2];
                    
                    VelWorldConstBias[0]=velocityWorld[0]/((float)filterSizeVel)+VelWorldConstBias[0];
                    VelWorldConstBias[1]=velocityWorld[1]/((float)filterSizeVel)+VelWorldConstBias[1];
                    VelWorldConstBias[2]=velocityWorld[2]/((float)filterSizeVel)+VelWorldConstBias[2];
                    
                    VelWorldPrevious[0]=VelWorldConstBias[0];
                    VelWorldPrevious[1]=VelWorldConstBias[1];
                    VelWorldPrevious[2]=VelWorldConstBias[2];
                }
                else{
                    VelWorldFiltered[0]=VelWorldPrevious[0]+(velocityWorld[0]-xVelWorldBuffer[0])/((float)filterSizeVel);
                    VelWorldFiltered[1]=VelWorldPrevious[1]+(velocityWorld[1]-yVelWorldBuffer[0])/((float)filterSizeVel);
                    VelWorldFiltered[2]=VelWorldPrevious[2]+(velocityWorld[2]-zVelWorldBuffer[0])/((float)filterSizeVel);
                    
                    VelWorldPrevious[0]=VelWorldFiltered[0];
                    VelWorldPrevious[1]=VelWorldFiltered[1];
                    VelWorldPrevious[2]=VelWorldFiltered[2];
                    
                    for(int j=0; j<(filterSizeVel-1);j++){
                        xVelWorldBuffer[j]=xVelWorldBuffer[j+1];
                        yVelWorldBuffer[j]=yVelWorldBuffer[j+1];
                        zVelWorldBuffer[j]=zVelWorldBuffer[j+1];
                    }
                    xVelWorldBuffer[filterSizeVel-1]=velocityWorld[0];
                    yVelWorldBuffer[filterSizeVel-1]=velocityWorld[1];
                    zVelWorldBuffer[filterSizeVel-1]=velocityWorld[2];
                    /*
                    velocityWorld[0]=velocityWorld[0]-VelWorldFiltered[0];
                    velocityWorld[1]=velocityWorld[1]-VelWorldFiltered[1];
                    velocityWorld[2]=velocityWorld[2]-VelWorldFiltered[2]; 
                    */
                    tempValueX=VelWorldFiltered[0];
                    tempValueY=VelWorldFiltered[1];
                    tempValueZ=VelWorldFiltered[2];  
                }   
            }
            else{
                log.info("corrupted delta time - no velocity update");
                velocityWorld=pose.getVelocity();
            }
            return velocityWorld;         
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
            a[2]=e31*v[0]+e32*v[1]+e33*v[2];
            return a;
        }
        public float[] matrixTimesMatrix(float[] m){
            float[] a=new float[9];
            a[0]=m[0]*e11+m[3]*e12+m[6]*e13;
            a[1]=m[1]*e11+m[4]*e12+m[7]*e13;
            a[2]=m[2]*e11+m[5]*e12+m[8]*e13;
            a[3]=m[0]*e21+m[3]*e22+m[6]*e23;
            a[4]=m[1]*e21+m[4]*e22+m[7]*e23;
            a[5]=m[2]*e21+m[5]*e22+m[8]*e23;
            a[6]=m[0]*e31+m[3]*e32+m[6]*e33;
            a[7]=m[1]*e31+m[4]*e32+m[7]*e33;
            a[8]=m[2]*e31+m[5]*e32+m[8]*e33;
            return a;
        }
        public float[] matrixPlusInpMatrix(float[] p){
            float[] a=new float[9];
            a[0]=p[0]+e11;
            a[1]=p[1]+e12;
            a[2]=p[2]+e13;
            a[3]=p[3]+e21;
            a[4]=p[4]+e22;
            a[5]=p[5]+e23;
            a[6]=p[6]+e31;
            a[7]=p[7]+e32;
            a[8]=p[8]+e33;
            return a;
        }
        public float[] matrixMinusInpMatrix(float[] p){
            float[] a=new float[9];
            a[0]=e11-p[0];
            a[1]=e12-p[1];
            a[2]=e13-p[2];
            a[3]=e21-p[3];
            a[4]=e22-p[4];
            a[5]=e23-p[5];
            a[6]=e31-p[6];
            a[7]=e32-p[7];
            a[8]=e33-p[8];
            return a;
        }
    }
}
