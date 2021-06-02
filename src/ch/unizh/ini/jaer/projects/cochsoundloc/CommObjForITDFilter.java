/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 * This object is used to send control commands to the ITDFilter
 *
 * @author Holger
 */
public class CommObjForITDFilter {
    private int command = 0; // 0=noJob, 1=startChannelCalibrationWithGivenITD, 2=StopThisChannelCalibration, 3=CalibratingAuditoryMap
    private float PlayingITD = 0;

    /**
     * @return the command
     * 0=noJob,
     * 1=startChannelCalibrationWithGivenITD,
     * 2=StopThisChannelCalibration,
     * 3=Disable AveragingDecay
     * 4=Enable AveragingDecay
     * 5=clear Bins
     */
    public int getCommand() {
        return command;
    }

    /**
     * @param command the command to set
     * 0=noJob,
     * 1=startChannelCalibrationWithGivenITD,
     * 2=StopThisChannelCalibration,
     * 3=Disable AveragingDecay
     * 4=Enable AveragingDecay
     * 5=clear Bins
     */
    public void setCommand(int command) {
        this.command = command;
    }

    /**
     * @return the PlayingITD
     */
    public float getPlayingITD() {
        return PlayingITD;
    }

    /**
     * @param PlayingITD the PlayingITD to set
     */
    public void setPlayingITD(float PlayingITD) {
        this.PlayingITD = PlayingITD;
    }
}
