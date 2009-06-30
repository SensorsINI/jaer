/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * This object is used to send information to the panTiltThread
 * 
 * @author Holger
 */
public class CommObjForPanTilt {
//    private boolean fromCochlea = false;
//    private boolean fromRetina = false;
    private int data = 0; // first bit = 0 if from cochlea or 1 if from retina
    private float panOffset = 0;
    private float tiltOffset = 0;
    private float confidence = 0;

    /**
     * @return the fromCochlea
     */
    public boolean isFromCochlea() {
        if ( ( data & 1 ) == 0 ){
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * @param fromCochlea the fromCochlea to set
     */
    public void setFromCochlea(boolean fromCochlea) {
        if (fromCochlea) {
            this.data = data | 1;
        }
        else {
            this.data = data & ~1;
        }
    }

    /**
     * @return the fromRetina
     */
    public boolean isFromRetina() {
        if ( ( data & 1 ) == 0 ){
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * @param fromRetina the fromRetina to set
     */
    public void setFromRetina(boolean fromRetina) {
        if (fromRetina) {
            this.data = data & ~1;
        }
        else {
            this.data = data | 1;
        }
    }

    /**
     * @return the panOffset
     */
    public float getPanOffset() {
        return panOffset;
    }

    /**
     * @param panOffset the panOffset to set
     */
    public void setPanOffset(float panOffset) {
        this.panOffset = panOffset;
    }

    /**
     * @return the tiltOffset
     */
    public float getTiltOffset() {
        return tiltOffset;
    }

    /**
     * @param tiltOffset the tiltOffset to set
     */
    public void setTiltOffset(float tiltOffset) {
        this.tiltOffset = tiltOffset;
    }

    /**
     * @return the confidence
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * @param confidence the confidence to set
     */
    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeFloat(this.panOffset);
        out.writeFloat(this.tiltOffset);
        out.writeFloat(this.confidence);
        out.writeInt(this.data);
        out.close();
        byte[] bytes = baos.toByteArray();
        return bytes;
    }

    public void setBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream baos = new ByteArrayInputStream(bytes);
        DataInputStream in = new DataInputStream(baos);
        this.panOffset = (float) in.readFloat();
        this.tiltOffset = (float) in.readFloat();
        this.confidence = (float) in.readFloat();
        this.data = (int) in.readInt();
        in.close();
    }
}
