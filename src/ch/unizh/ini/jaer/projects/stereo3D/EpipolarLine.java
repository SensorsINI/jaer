/*
 * Event3D.java
 *
 * Created on June 17, 2008, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

/**
 * Represent an epipolar line
 * @author rogister
 */
public class EpipolarLine {

    public float[] value;

    public EpipolarLine(int size) {
        value = new float[size];
    }
}