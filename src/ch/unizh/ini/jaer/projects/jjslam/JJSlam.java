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
 * Jean-Jaques Slotine SLAM without linearisation
 *
 * @author Robin Deuber, Tobi Delbruck
 */
@Description("Jean-Jaques Slotine SLAM without linearisation")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class JJSlam extends EventFilter2D implements FrameAnnotater{
    private TextRenderer textRenderer = null;
    private CameraPose cameraPose = new CameraPose();
    private CameraAccRotVel cameraAccRotVel = new CameraAccRotVel();
    //Adapt Path accordingt to your file system to save values in a txt-file
    private WriteFile writeFile = new WriteFile("C:\\Users\\Robin\\polybox\\JJSLAM_Semester_Thesis\\asdfasdf.txt");
    private ImuMedianTracker tracker = null;
    private Landmark landmark1 = new Landmark();
    private int i=0; 
    private float gainPropertional = getFloat("gainPropertional", 1f);
    private float cameraFocalLengthMm = getFloat("cameraFocalLengthMm", 8);


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
        //Store the acceleration, the rotational velocity and the corresponding time
        cameraAccRotVel.setRotVel(tiltRateDpsSample, yawRateDpsSample, rollRateDpsSample);
        cameraAccRotVel.setAcc(accX, accY, accZ );
        cameraAccRotVel.setTimeUs(t_current);
        cameraPose.setTimeUs(t_current);
        
        //Calculate the current orienation and update the camera pose object
        float[] orientation=cameraAccRotVel.updateOrientation(cameraPose);
        cameraPose.setOrientation(orientation);
        //Calculate the estimated velocity and update the camera pose object
        float[] velocity=cameraAccRotVel.updateVelocityWorldIIR(cameraPose);
        cameraPose.setVelocity(velocity);
        //Update the counter (used to count the calibration procedures)
        i=i+1;
        
        //Use the landmark object to estimate the position of the lankmark (not ready yet)
        //landmark1.estimatePose(cameraAccRotVel, cameraPose);
        
        //Write into a txt-file as in the following example
        //writeFile.writeToFile(cameraPose.writeFileStringVelocity());
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
            textRenderer = new TextRenderer(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        }
        if(i<3000){
            textRenderer.beginRendering(sx, sy);
            gl.glColor4f(1, 0, 0, 1f);
            textRenderer.draw("Calibration! - Do not move!",100, (sy / 2));
            textRenderer.endRendering();
        }
        else{
            textRenderer.beginRendering(sx, sy);
            gl.glColor4f(0, 1, 0, .7f);
            textRenderer.draw("Velocity x,y",sx-70, sy/2+95);
            textRenderer.draw("Orientation z",15, sy/2+95);
            //textRenderer.draw(cameraPose.toStringOrientation(), 1, sy / 2);
            //textRenderer.draw(cameraPose.toStringVelocity(),1, (sy / 2-20));
            textRenderer.endRendering();
            //Draw the velocity
            int decide=1; //Set to one for velocity like the tracked objects, set to -1 for velocity of the DVS in world frame
            float Xvel=decide*cameraPose.getVelocity()[0];
            float Yvel=decide*cameraPose.getVelocity()[1];
            float arrowFactor=50.0f/0.3f;
            float edgelenth=50*((float)Math.sqrt(Xvel*Xvel+Yvel*Yvel));
            float angle1=(float)(Math.toRadians(45.0)+Math.atan2(Yvel, Xvel));
            float angle2=(float)(Math.atan2(Yvel,Xvel)-Math.toRadians(45));
            Point2D arrowStart = new Point2D.Float();
            arrowStart.setLocation(sx+75,sy/2+20);
            Point2D head = new Point2D.Float();
            head.setLocation(arrowStart.getX()-arrowFactor*Xvel, arrowStart.getY()+arrowFactor*Yvel);
            gl.glBegin(GL2.GL_LINES);
            gl.glColor3f(0, 1, 0);
            gl.glVertex2d(arrowStart.getX(), arrowStart.getY());
            gl.glVertex2d(head.getX(), head.getY());
            gl.glVertex2d(head.getX(), head.getY());
            gl.glVertex2d(head.getX()+Math.sin(angle1)*edgelenth, head.getY()+Math.cos(angle1)*edgelenth);
            gl.glVertex2d(head.getX(), head.getY());
            gl.glVertex2d(head.getX()-Math.sin(angle2)*edgelenth, head.getY()-Math.cos(angle2)*edgelenth);
            gl.glEnd(); 
            /*
            //Draw the y-velocity
            Point2D midPoint= new Point2D.Float();
            midPoint.setLocation(sx+75,sy/2-100);
            float halfLenght=cameraPose.getVelocity()[2]*100f;
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3f(0, 1, 0);
            gl.glVertex2d(midPoint.getX() - halfLenght, midPoint.getY() - halfLenght);
            gl.glVertex2d(midPoint.getX() + halfLenght, midPoint.getY() - halfLenght);
            gl.glVertex2d(midPoint.getX() + halfLenght, midPoint.getY() + halfLenght);
            gl.glVertex2d(midPoint.getX() - halfLenght, midPoint.getY() + halfLenght);
            gl.glEnd();
            */
            //Draw the z-orientation
            float L=50;
            float orientZ= -(float)Math.PI*cameraPose.getOrientation()[2]/((float)180f);
            Point2D bearing = new Point2D.Float();
            gl.glBegin(GL2.GL_LINES);
            gl.glColor3f(0, 1, 0);
            bearing.setLocation(-85,sy/2+20);
            gl.glVertex2d(bearing.getX(), bearing.getY());
            gl.glVertex2d(bearing.getX()+ (float)L*Math.cos(orientZ), bearing.getY() - (float)L*Math.sin(orientZ));
            gl.glVertex2d(bearing.getX(), bearing.getY());
            gl.glVertex2d(bearing.getX()- (float)L*Math.cos(orientZ), bearing.getY() + (float)L*Math.sin(orientZ));
            gl.glEnd();
                    }
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
        //current time stamp
        int t_curr;
        //x is a vector for the position
        float[] x = new float[3]; 
        //u is a vector for the camera velocity in the world fixed frame
        float[] u = new float[3];
        //phi is a vector for the orienation, phiRotMat is the corresponding rotation matrix
        float[] phi = new float[3];
        Matrix3 phiRotMat = new Matrix3();
        
        //Method to produce a string with the current orientation for rendering
        public String toStringOrientation() {
            return String.format("CameraPose: [orientation]=[%.2f,%.2f,%.2f]",
                    phi[0], phi[1], phi[2]);
        }
        //Method to produce a string with the current orientation for writing in txt-file
        public String writeFileSringOrientation(){
            return String.format("%.2f,%.2f,%.2f, %d",
                    phi[0], phi[1], phi[2],t_curr);
        }
        //Method to produce a string with the current velocity for writing in txt-file
        public String writeFileStringVelocity(){
            return String.format("%f,%f,%f",u[0],u[1], u[2]);
        }
        //Method to produce a string with the current velocity for writing in for rendering
        public String toStringVelocity(){
            return String.format("CameraVelocity: [ux,uy,uz]=[%.2f,%.2f,%.2f]", 
                    u[0],u[1], u[2]);
        }
        public void setOrientation( float[] a) {
            //Set the orientation angle values
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
            //Method to set the velocity
            u[0]=a[0];
            u[1]=a[1];
            u[2]=a[2];
        }
        public void setTimeUs(int a){
            //Method to set the time in microseconds
            t_curr=a;
        }
        public float[] getOrientation() {
            //Method to get the current orientation
            float[] a = new float[3];
            a[0]=phi[0];
            a[1]=phi[1];
            a[2]=phi[2];
            return a;  
        }
        public float[] getVelocity(){
            //Method to get the current velocity
            float[] a = new float[3];
            a[0]=u[0];
            a[1]=u[1];
            a[2]=u[2];
            return a;
        }
        public Matrix3 getPhiRotMa(){
            //Method to get the rotation matrix
            return phiRotMat;
        }
    }
    private class WriteFile {
        //Class to write into a txt-file
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
            
    private class Landmark {
        //NOT FINISHED YET
        //Landmark object
        private Matrix3 P = new Matrix3();
        private Matrix3 Q = new Matrix3();
        private Matrix3 Omega = new Matrix3();
        private Matrix3 HRH =new Matrix3();
        //Rinv and H are filled as follow: H[0]=h11, H[1]=h12,...
        float Rinv[]={0, 0, 0, 0};
        float H[]= new float[6];
        
        public void estimatePose(CameraAccRotVel AccRotVel, CameraPose cameraPose) {
            //Method to estimate the camera position
            //Set the constants
            float valuesQ[]={0,0,0,0,0,0,0,0,0};
            Q.setValuesArray(valuesQ);
            //Change the directions of the linear velocity (frame transformation)
            float velocityWorld[]=cameraPose.getVelocity();
            float velocityJJ[] = new float[3];
            velocityJJ[0]=velocityWorld[0];
            velocityJJ[1]=-velocityWorld[1];
            velocityJJ[2]=velocityWorld[2];
            //Change the values of the rotational velocity (frame transformation)
            float rotVelBody[]=AccRotVel.getRotVel();
            float rotVelJJ[] = new float[3];
            rotVelJJ[0]=rotVelBody[0];
            rotVelJJ[1]=-rotVelBody[1];
            rotVelJJ[2]=rotVelBody[2];
            //Update the omega matrix (Corrdinate System!)
            Omega.setValuesIndividual(0f, -rotVelJJ[2], rotVelJJ[1], rotVelJJ[2], 0f, -rotVelJJ[0], -rotVelJJ[1], rotVelJJ[0], 0f);
            //Fill H with the information of the tracker (different coordinate System!)
            
            //Calculate H*R*H
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
           
            //Calculate x in the conventional frame and use it as return value
            
        }
    }        
    private class CameraAccRotVel {
        //object for the linear acceleration and the rotational velocity
        //acceleration in the body fixed frame
        float[] accBody = new float[3];
        //acceleration in the world fixed frame
        float[] accWorld = new float[3];
        //rotational velocity in the body fixed frame
        float[] rotVelBody = new float[3];
        //current and previous time stamp and their difference
        int timeUs_curr = 0;
        int timeUs_prev = 0;
        int dTUs=0;
        
        //Variables for storing previous values for the orientation moving averaging filters
        int movAvSize=150;
        float[] buffer_x_orientation_complementary = new float[movAvSize];
        float[] buffer_z_orientation_complementary = new float[movAvSize];
        float previous_x_orientation_complementary=0;
        float previous_z_orientation_complementary=0;
        private float xRotVelInitalSum=0;
        private float yRotVelInitalSum=0;
        private float zRotVelInitalSum=0;
        private float xCompInitial=0;
        private float zCompInitial=0;
        
        //Variables for storing the values of the acceleration moving averaging filters
        int filterSizeAcc=3000;
        int filterSizeVel=10000;
        float[] xAccWorldBuffer = new float[filterSizeAcc];
        float[] yAccWorldBuffer = new float[filterSizeAcc];
        float[] zAccWorldBuffer = new float[filterSizeAcc];
        float[] AccWorldPrevious = new float[3];
        float[] AccWorldConstBias = new float[3];
        float[] VelWorldUnfilteredPrevious = new float[3];
        
        //Variable for storing the values of the velocity moving averaging filters
        float[] xVelWorldBuffer = new float[filterSizeVel];
        float[] yVelWorldBuffer = new float[filterSizeVel];
        float[] zVelWorldBuffer = new float[filterSizeVel];
        float[] VelWorldPrevious = new float[3];
        float[] VelWorldConstBias = new float[3];
        
        //Variable for the velocity IIR Filter
        double[] prevRawVel =new double[3];
        double[] prevPrevRawVel =new double [3];
        double[] prevFilteredVel = new double[3];
        double[] prevprevFilteredVel =new double[3];
  
        //Method to produce a string with acceleration in body fixed frame for rendering
        public String toStringAccelerationBody() {
            return String.format("Accelartion Body: [x,y,z]=[%.2f,%.2f,%.2f]",
                    accBody[0], accBody[1], accBody[2]);
        }
        //Method to produce a string with acceleration in world fixed frame for rendering
        public String toStringAccelerationWorld() {
            return String.format("Accelartion World: [x,y,z]=[%.2f,%.2f,%.2f]",
                    accWorld[0], accWorld[1], accWorld[2]);
        }
        //Method to produce a string with rotational velocity in body fixed frame for rendering
        public String toStringRotVelRaw() {
            return String.format("RotVel: [x,y,z]=[%.2f,%.2f,%.2f]",
                    rotVelBody[0], rotVelBody[1], rotVelBody[2]);
        }
        //Method to produce a string with full information for writing in txt-file
        public String toStringExportComplete(CameraPose pose){
            float[] accWorldLocal = new float[3];
            accWorldLocal=pose.getPhiRotMa().matrixTimesVector(accBody);
            return String.format("%f,%f,%f,%f,%f,%f,%f,%f,%f,%d,%d",accBody[0], accBody[1], accBody[2],accWorldLocal[0], accWorldLocal[1], accWorldLocal[2],pose.getOrientation()[0],pose.getOrientation()[1],pose.getOrientation()[2],timeUs_curr, dTUs);
        }
        //Method to set the acceleration
        public void setAcc (float a, float b, float c )
        {
            accBody[0]=-a;
            accBody[1]=-b;
            accBody[2]=c;
        }
        //Method to set the rotational velocity (body fixed frame)
        public void setRotVel (float a, float b, float c)
        {
            rotVelBody[0]=a;
            rotVelBody[1]=b;
            rotVelBody[2]=c;
        }
        //Method to get the rotational velocity (body fixed frame)
        public float[] getRotVel (){
            float[] a = new float[3];
            a[0]=rotVelBody[0];
            a[1]=rotVelBody[1];
            a[2]=rotVelBody[2];
            return a;
        }
        //Method to set the current, previous and delta time
        public void setTimeUs (int time_current)
        {
            timeUs_prev = timeUs_curr;
            timeUs_curr = time_current;
            dTUs=timeUs_curr-timeUs_prev;
        }
        //Method to update the orientation
        public float[] updateOrientation(CameraPose pose){
            //Vector for the orientation estimated by integration
            float[] current_orientation_integration = new float[3];
            //Vector for the fused orientation
            float[] fused_orientation_integration = new float[3];
            //Variable for the orientation estimated by gravit-orientation
            float current_x_orientation_complementary=0;
            float current_z_orientation_complementary=0;
            float filtered_x_orientation_complementary=0;
            float filtered_z_orientation_complementary=0;
            //Variable for the moving average filter of the gravity-based orientation
            float movAvSumX=0;
            float movAvSumZ=0;
            //Constant for the orientation fusion (design parameter!)
            float fusionConstant=0.8f;
            //Delta time sanity check
            if ((dTUs>0) && (dTUs<100000)){
                //Integration the rotational velocity with scaling and bias subtraction
                current_orientation_integration[0]=pose.getOrientation()[0]+(rotVelBody[0]-(xRotVelInitalSum/3000f))*(360f/344f)*dTUs*0.000001f;
                current_orientation_integration[1]=pose.getOrientation()[1]+(rotVelBody[1]-(yRotVelInitalSum/3000f))*(360f/348f)*dTUs*0.000001f;
                current_orientation_integration[2]=pose.getOrientation()[2]+(rotVelBody[2]-(zRotVelInitalSum/3000f))*(360f/323f)*dTUs*0.000001f;
                
                //Estimating the orientation by using the gravity
                current_x_orientation_complementary=(float)-Math.atan2((accBody[2]), (Math.signum(accBody[1])*(Math.sqrt((accBody[0]*accBody[0])+(accBody[1]*accBody[1])))));
                current_z_orientation_complementary=(float)-Math.atan2((-accBody[0]),accBody[1]);
               
                //If the moving average buffer isn't filled entirely the following is done:
                if(i<movAvSize)
                {
                    //Fill the buffer with gravity-based orientation
                    buffer_x_orientation_complementary[i]=current_x_orientation_complementary;
                    buffer_z_orientation_complementary[i]=current_z_orientation_complementary;
                    //Sum up the all gravity-based orientation values and average them
                    for (int j=0; j<(i+1); j++){
                        movAvSumX=movAvSumX+buffer_x_orientation_complementary[j];
                        movAvSumZ=movAvSumZ+buffer_z_orientation_complementary[j];
                    }
                    filtered_x_orientation_complementary=movAvSumX/((float)(i+1));
                    filtered_z_orientation_complementary=movAvSumZ/((float)(i+1));
                }
                //If the buffer is filled, the followin is done:
                else
                {
                    //Moving averaging filter
                    filtered_x_orientation_complementary=previous_x_orientation_complementary+((current_x_orientation_complementary-buffer_x_orientation_complementary[0])/(float)movAvSize);
                    filtered_z_orientation_complementary=previous_z_orientation_complementary+((current_z_orientation_complementary-buffer_z_orientation_complementary[0])/(float)movAvSize);
                    //Update the moving avering buffer
                    for(int j=0; j<(movAvSize-1);j++){
                        buffer_x_orientation_complementary[j]=buffer_x_orientation_complementary[j+1];
                        buffer_z_orientation_complementary[j]=buffer_z_orientation_complementary[j+1];
                    }
                    buffer_x_orientation_complementary[movAvSize-1]=current_x_orientation_complementary;
                    buffer_z_orientation_complementary[movAvSize-1]=current_z_orientation_complementary;
                }
                //Save the filtered values for the next iteration
                previous_x_orientation_complementary=filtered_x_orientation_complementary;
                previous_z_orientation_complementary=filtered_z_orientation_complementary;
                
                //Fusion of the gravity-based and the integration based orientation
                fused_orientation_integration[0]=(1-fusionConstant)*((filtered_x_orientation_complementary-(xCompInitial/3000f))*180.0f/((float)(Math.PI)))+fusionConstant*current_orientation_integration[0];
                fused_orientation_integration[1]=current_orientation_integration[1];
                fused_orientation_integration[2]=(1-fusionConstant)*((filtered_z_orientation_complementary-(zCompInitial/3000f))*180.0f/((float)(Math.PI)))+fusionConstant*current_orientation_integration[2];
            }
            //Case if the rotational didn't pass the sanity check
            else {
                log.info("corrupted delta time - no orientation update");
                fused_orientation_integration = pose.getOrientation();
            }
            //Constant offset estimation in the beginning
            if(i<3000){
                //Constat offset of the rotational velocity
                xRotVelInitalSum=xRotVelInitalSum+rotVelBody[0];
                yRotVelInitalSum=yRotVelInitalSum+rotVelBody[1];
                zRotVelInitalSum=zRotVelInitalSum+rotVelBody[2];
                //Constant offset of the gravity-based estimation
                xCompInitial=xCompInitial+filtered_x_orientation_complementary;
                zCompInitial=zCompInitial+filtered_z_orientation_complementary;
                //No orientation update during this time:
                fused_orientation_integration=pose.getOrientation();
                log.info("Estimating constant bias of rotational velocity");
            }
             
            return fused_orientation_integration;
        }
        //Method to update the velocity estimation by using a MOVING AVERAGE FILTER
        public float[] updateVelocityWorldMAF(CameraPose pose){
            //Vector to store the velocity in world fixed frame
            float[] velocityWorld = new float[3];
            //Rotation Matrix of the current orientation
            Matrix3 phiRotMat=pose.getPhiRotMa();
            //Vector for the filtered acceleration in world fixed frame
            float[] AccWorldFiltered = new float[3];
            //Vector for the filtered velocity in world fixed frame
            float[] VelWorldFiltered = new float[3];
            
            //Sanity check of the delta time
            if ((dTUs>0) && (dTUs<100000)){
                //Calculation of the rotation matrix
                accWorld=phiRotMat.matrixTimesVector(accBody);
                //Subract the constant offset due to gravity
                accWorld[0]=accWorld[0];
                accWorld[1]=accWorld[1]-1f;
                accWorld[2]=accWorld[2];
                
                //If the buffer of the filter is not filled completely:
                if(i<(filterSizeAcc)){
                    //Fill the buffer
                    xAccWorldBuffer[i]=accWorld[0];
                    yAccWorldBuffer[i]=accWorld[1];
                    zAccWorldBuffer[i]=accWorld[2];
                    //Update the averaged buffer value
                    AccWorldConstBias[0]=accWorld[0]/((float)filterSizeAcc)+AccWorldConstBias[0];
                    AccWorldConstBias[1]=accWorld[1]/((float)filterSizeAcc)+AccWorldConstBias[1];
                    AccWorldConstBias[2]=accWorld[2]/((float)filterSizeAcc)+AccWorldConstBias[2];
                    
                    AccWorldPrevious[0]=AccWorldConstBias[0];
                    AccWorldPrevious[1]=AccWorldConstBias[1];
                    AccWorldPrevious[2]=AccWorldConstBias[2];
                    
                    velocityWorld=pose.getVelocity();
                }
                //Once the buffer is filled, the following is done:
                else{
                    //The acceleration is filtered by a moving average filter in the following step:
                    AccWorldFiltered[0]=AccWorldPrevious[0]+(accWorld[0]-xAccWorldBuffer[0])/((float)filterSizeAcc);
                    AccWorldFiltered[1]=AccWorldPrevious[1]+(accWorld[1]-yAccWorldBuffer[0])/((float)filterSizeAcc);
                    AccWorldFiltered[2]=AccWorldPrevious[2]+(accWorld[2]-zAccWorldBuffer[0])/((float)filterSizeAcc);
                    //Save the value for the next iteration
                    AccWorldPrevious[0]=AccWorldFiltered[0];
                    AccWorldPrevious[1]=AccWorldFiltered[1];
                    AccWorldPrevious[2]=AccWorldFiltered[2];
                    //Update the filter buffer
                    for(int j=0; j<(filterSizeAcc-1);j++){
                        xAccWorldBuffer[j]=xAccWorldBuffer[j+1];
                        yAccWorldBuffer[j]=yAccWorldBuffer[j+1];
                        zAccWorldBuffer[j]=zAccWorldBuffer[j+1];
                    }
                    xAccWorldBuffer[filterSizeAcc-1]=accWorld[0];
                    yAccWorldBuffer[filterSizeAcc-1]=accWorld[1];
                    zAccWorldBuffer[filterSizeAcc-1]=accWorld[2];
                    
                    //Integrate the filtered acceleration 
                    velocityWorld[0]=VelWorldUnfilteredPrevious[0]+(accWorld[0]-AccWorldFiltered[0])*dTUs*9.81f*0.000001f;
                    velocityWorld[1]=VelWorldUnfilteredPrevious[1]+(accWorld[1]-AccWorldFiltered[1])*dTUs*9.81f*0.000001f;
                    velocityWorld[2]=VelWorldUnfilteredPrevious[2]+(accWorld[2]-AccWorldFiltered[2])*dTUs*9.81f*0.000001f; 
                    
                    //Save the unfiltered velocity estimation
                    VelWorldUnfilteredPrevious[0]=velocityWorld[0];
                    VelWorldUnfilteredPrevious[1]=velocityWorld[1];
                    VelWorldUnfilteredPrevious[2]=velocityWorld[2];
                }
                
                //If the buffer of the filter is not filled completely:
                if(i<(filterSizeVel)){
                    //Fill the buffer with the unfiltered velocity
                    xVelWorldBuffer[i]=velocityWorld[0];
                    yVelWorldBuffer[i]=velocityWorld[1];
                    zVelWorldBuffer[i]=velocityWorld[2];
                    //Estimate the constant velocity offset
                    VelWorldConstBias[0]=velocityWorld[0]/((float)filterSizeVel)+VelWorldConstBias[0];
                    VelWorldConstBias[1]=velocityWorld[1]/((float)filterSizeVel)+VelWorldConstBias[1];
                    VelWorldConstBias[2]=velocityWorld[2]/((float)filterSizeVel)+VelWorldConstBias[2];
                    
                    VelWorldPrevious[0]=VelWorldConstBias[0];
                    VelWorldPrevious[1]=VelWorldConstBias[1];
                    VelWorldPrevious[2]=VelWorldConstBias[2];
                }
                //Once the buffer is filled, the following is done:
                else{
                    //The velocity is filtered with a Moving Average Filter
                    VelWorldFiltered[0]=VelWorldPrevious[0]+(velocityWorld[0]-xVelWorldBuffer[0])/((float)filterSizeVel);
                    VelWorldFiltered[1]=VelWorldPrevious[1]+(velocityWorld[1]-yVelWorldBuffer[0])/((float)filterSizeVel);
                    VelWorldFiltered[2]=VelWorldPrevious[2]+(velocityWorld[2]-zVelWorldBuffer[0])/((float)filterSizeVel);
                    
                    //Saving the filtered value for the next iteration
                    VelWorldPrevious[0]=VelWorldFiltered[0];
                    VelWorldPrevious[1]=VelWorldFiltered[1];
                    VelWorldPrevious[2]=VelWorldFiltered[2];
                    
                    //Update the filter buffer
                    for(int j=0; j<(filterSizeVel-1);j++){
                        xVelWorldBuffer[j]=xVelWorldBuffer[j+1];
                        yVelWorldBuffer[j]=yVelWorldBuffer[j+1];
                        zVelWorldBuffer[j]=zVelWorldBuffer[j+1];
                    }
                    xVelWorldBuffer[filterSizeVel-1]=velocityWorld[0];
                    yVelWorldBuffer[filterSizeVel-1]=velocityWorld[1];
                    zVelWorldBuffer[filterSizeVel-1]=velocityWorld[2];
                    
                    //Subtract the drift from the raw value to get the final filtered velocity
                    velocityWorld[0]=velocityWorld[0]-VelWorldFiltered[0];
                    velocityWorld[1]=velocityWorld[1]-VelWorldFiltered[1];
                    velocityWorld[2]=velocityWorld[2]-VelWorldFiltered[2]; 
                }   
            }
            else{
                //The delta time is corrputed
                log.info("corrupted delta time - no velocity update");
                velocityWorld=pose.getVelocity();
            }
            return velocityWorld;         
        }  
        public float[] updateVelocityWorldIIR(CameraPose pose){
            float[] velocityWorld = new float[3];
            double[] currFilteredValue = new double[3];
            double[] currRawValue = new double[3];
            Matrix3 phiRotMat=pose.getPhiRotMa();
            if ((dTUs>0) && (dTUs<100000)){
                accWorld=phiRotMat.matrixTimesVector(accBody);
                //Filter Coefficients (normalized edge frequncy 0.00333
                double a1=1f;
                double a2=-1.990361789279116f;
                double a3=0.990515645026782f;
                double b1=0.704561349441701f;
                double b2=-1.409122698883402f;
                double b3=0.704561349441701f;
                
                
                if(i<(filterSizeAcc)){
                    //If the constant offset is not estimated yet, it is calculated here:
                    AccWorldConstBias[0]=accWorld[0]/((float)filterSizeAcc)+AccWorldConstBias[0];
                    AccWorldConstBias[1]=accWorld[1]/((float)filterSizeAcc)+AccWorldConstBias[1];
                    AccWorldConstBias[2]=accWorld[2]/((float)filterSizeAcc)+AccWorldConstBias[2];
                }
                else
                {   
                    //Calculate the current raw velocity value
                    currRawValue[0]=prevRawVel[0]+(accWorld[0]-AccWorldConstBias[0])*((double)dTUs)*9.81*0.000001;
                    currRawValue[1]=prevRawVel[1]+(accWorld[1]-AccWorldConstBias[1])*((double)dTUs)*9.81*0.000001;
                    currRawValue[2]=prevRawVel[2]+(accWorld[2]-AccWorldConstBias[2])*((double)dTUs)*9.81*0.000001; 
                    
                    //Filtering step of the velocity with IIR
                    currFilteredValue[0]=(b1*currRawValue[0]+b2*prevRawVel[0]+b3*prevPrevRawVel[0]-a2*prevFilteredVel[0]-a3*prevprevFilteredVel[0])/a1;
                    currFilteredValue[1]=(b1*currRawValue[1]+b2*prevRawVel[1]+b3*prevPrevRawVel[1]-a2*prevFilteredVel[1]-a3*prevprevFilteredVel[1])/a1;
                    currFilteredValue[2]=(b1*currRawValue[2]+b2*prevRawVel[2]+b3*prevPrevRawVel[2]-a2*prevFilteredVel[2]-a3*prevprevFilteredVel[2])/a1;
                    
                    //Store the result in the velocityWorld variable
                    velocityWorld[0]=(float)currFilteredValue[0];
                    velocityWorld[1]=(float)currFilteredValue[1];
                    velocityWorld[2]=(float)currFilteredValue[2];
                    
                    //Do the update of the storage variables
                    prevprevFilteredVel=prevFilteredVel;
                    prevFilteredVel=currFilteredValue;
                    prevPrevRawVel=prevRawVel;
                    prevRawVel=currRawValue;
                }
            }
            else{
            //If the delta time would be corrupted anyhow
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
    //Class for easy handling 3x3 matrices
    private class Matrix3 {
        //Matrix elements eij (i is the colum, j is the row)
        float e11;
        float e12;
        float e13;
        float e21;
        float e22;
        float e23;
        float e31;
        float e32;
        float e33;
        
        //Fill the matrix with single numbers
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
        //Fill the matrix with an array. 
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
        //Get an array with the matrix elements. 
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
        //Get an arreay of the transposed matrix elements.
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
        //Get a vector a=Matrix*v
        public float[] matrixTimesVector(float [] v){
            float[] a=new float[3];
            a[0]=e11*v[0]+e12*v[1]+e13*v[2];
            a[1]=e21*v[0]+e22*v[1]+e23*v[2];
            a[2]=e31*v[0]+e32*v[1]+e33*v[2];
            return a;
        }
        //Get a matrix a=matrix*m
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
        //Get a matrix a=matrix+p
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
        //Get a matrix a=matrix-p
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
