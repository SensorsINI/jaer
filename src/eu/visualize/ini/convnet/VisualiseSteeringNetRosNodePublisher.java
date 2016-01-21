package eu.visualize.ini.convnet;
import eu.visualize.ini.retinamodel.*;
import net.sf.jaer.Description;

/**
 * RosJava node supplying data to ROS master.
 * This publisher supplies steering CNN network output units.
 * These units encode the activity of the four outputs
 * [left, center, right, invisible}
 * 
 @author Tobi
*/
@Description("RosJava node supplying data to ROS master")
public class VisualiseSteeringNetRosNodePublisher {
    /** Steering net winning output
     * 0=left, 1=center, 2=right, 3=no target
     */
    private int output;

    /**
     * 0=left, 1=center, 2=right, 3=no target
     * @return the output
     */
    public int getOutput() {
        return output;
    }

    /**
     * 0=left, 1=center, 2=right, 3=no target
     * @param output the output to set
     */
    public void setOutput(int output) {
        this.output = output;
    }
    
}
