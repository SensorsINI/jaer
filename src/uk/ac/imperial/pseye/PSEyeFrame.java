
package uk.ac.imperial.pseye;

/* Package private wrapper class for PSEye frmae data */
class PSEyeFrame {
    private long timeStamp;
    private int[] data;
    private PSEyeCamera.Resolution resolution = null;
        
    public PSEyeFrame() {
    }
        
    public int getSize() {
        switch (resolution) {
            case VGA: return 640 * 480;
            case QVGA:  return 320 * 240;
            default: return 0; // should really raise an error
        }
    }
    
    public void setResolution(PSEyeCamera.Resolution resolution) {
        if (resolution != this.resolution) {
            this.resolution = resolution;
            data = new int[getSize()];
        }
    }
    
    public long getTimeStamp() {
        return timeStamp;
    }
    
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
    
    public int[] getData() {
        return data;
    }
    
    public void copyData(int[] target, int offset) {
        // copy frame data to passed array
        System.arraycopy(data, 0, target, offset, getSize());
    }
}