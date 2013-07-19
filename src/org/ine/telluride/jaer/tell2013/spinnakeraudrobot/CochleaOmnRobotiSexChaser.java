/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2013.spinnakeraudrobot;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

import org.ine.telluride.jaer.tell2009.CochleaGenderClassifier;
import org.ine.telluride.jaer.tell2009.CochleaGenderClassifier.Gender;
import org.ine.telluride.jaer.tell2013.spinnakeraudrobot.OmniRobotControl.MotorCommand;

import ch.unizh.ini.jaer.projects.cochsoundloc.ITDFilter;

/**
 * Uses ITDFilter and CochleaGenderClassifier to to control OmniRobot to steer towards sound sound for Telluride 2013 UNS project
 *
 * @author tobi
 */
@Description("Uses ITDFilter and CochleaGenderClassifier to to control OmniRobot to steer towards sound sound for Telluride 2013 UNS project")
public class CochleaOmnRobotiSexChaser extends EventFilter2D implements FrameAnnotater{

    private ITDFilter itdFilter;
//    private ISIFilter isiFilter;
    private OmniRobotControl omniRobotControl;
    private CochleaGenderClassifier genderClassifier;
    private int bestItd = -1;
    private int maxSpeed=getInt("maxSpeed",70);
    private Gender desiredGender=Gender.valueOf(getString("desiredGender",Gender.Unknown.toString()));

    public CochleaOmnRobotiSexChaser(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        filterChain.add(omniRobotControl=new OmniRobotControl(chip));
        filterChain.add(itdFilter = new ITDFilter(chip));
//        filterChain.add(isiFilter = new ISIFilter(chip));
        filterChain.add(genderClassifier=new CochleaGenderClassifier(chip));
        setEnclosedFilterChain(filterChain);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        getEnclosedFilterChain().filterPacket(in);
        int currentBestItd = itdFilter.getBestITD();
        Gender gender=genderClassifier.getGender();
        if(gender!=desiredGender) return in; // take no action unless we hear desired gender
        if (currentBestItd != bestItd) { // only do something if bestItdBin changes
            bestItd = currentBestItd;
            // here is the business logic
            float err=(bestItd)/(float)itdFilter.getMaxITD();
            int speed=(int)Math.abs((maxSpeed*err));
                omniRobotControl.setSpeed(speed);
            if (err>0) {
                omniRobotControl.sendMotorCommand(MotorCommand.cw);
            } else {
                omniRobotControl.sendMotorCommand(MotorCommand.ccw);
            }
            log.info(String.format("err=%-8.2f speed=%-10d",err,speed));
        }
        return in;
    }


    @Override
    public void initFilter() {
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }

    
    public int getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(int speed) {
        this.maxSpeed=speed;
        putInt("maxSpeed",maxSpeed);
    }

    /**
     * @return the desiredGender
     */
    public Gender getDesiredGender() {
        return desiredGender;
    }

    /**
     * @param desiredGender the desiredGender to set
     */
    public void setDesiredGender(Gender desiredGender) {
        this.desiredGender = desiredGender;
        putString("desiredGender",desiredGender.toString());
    }
    
    

}
