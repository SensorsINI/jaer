package eu.visualize.ini.retinamodel;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.Observer;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

//-- Description -------------------------------------------------------------//
// Models multiple object motion cells that are excited by on or off activity
// within their classical receptive field but are inhibited by synchronous on or
// off activity in their extended RF, such as that caused by a saccadic eye
// movement. Also gives direction of movement of object
// @author diederik
@Description("Plots the output of the FPGA OMCs")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class OmcodFpgaVisualizer extends AbstractRetinaModelCell implements FrameAnnotater, Observer {

    public RosNodePublisher RosNodePublisher = new RosNodePublisher();
    private int lastCheckTimestampUs;
    private int nxmax;
    private int nymax;
    private int counter = 0;
    private int counterP = 0;
    private int lastOMCTimeStamp = 0;
    private int lastIndex = 0;
    private boolean DrawFire = false;
    private int[] lastSpikedOMC; // save the OMC cell that last spiked
    private int[][] lastSpikedOMCArray; // save the OMC cells that last spiked    
    final int subsample = 4;

    public OmcodFpgaVisualizer(AEChip chip) {
        super(chip);
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCArray = new int[3][2];
        chip.addObserver(this);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket temp = (EventPacket) in;
        checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak. 
        if (temp == null) {
            return in;
        }
        for (Object o : temp) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.special || e.isFilteredOut()) {
                DrawFire = false;
                continue;
            }
            // Check for firing cells and annotate
            if ((e.x == 32 && e.y == 32) || (e.x == 96 && e.y == 32) || (e.x == 32 && e.y == 96) || (e.x == 96 && e.y == 96) || (e.x == 64 && e.y == 64)) {
                //Store all spiked cells
                if (e.x == 32 && e.y == 32) {
                    lastSpikedOMC[0] = 1;
                    lastSpikedOMC[1] = 1;
                }
                if (e.x == 96 && e.y == 32) {
                    lastSpikedOMC[0] = 5;
                    lastSpikedOMC[1] = 1;
                }
                if (e.x == 32 && e.y == 96) {
                    lastSpikedOMC[0] = 1;
                    lastSpikedOMC[1] = 5;
                }
                if (e.x == 96 && e.y == 96) {
                    lastSpikedOMC[0] = 5;
                    lastSpikedOMC[1] = 5;
                }
                if (e.x == 64 && e.y == 64) {
                    lastSpikedOMC[0] = 3;
                    lastSpikedOMC[1] = 3;
                }
                lastOMCTimeStamp = e.timestamp;
                DrawFire = true;
                for (int i = 0; i < 3; i++) {
                    if (i == 0) { //store x
                        lastSpikedOMCArray[i][counter] = lastSpikedOMC[0];
                    }
                    if (i == 1) { //store y
                        lastSpikedOMCArray[i][counter] = lastSpikedOMC[1];
                    }
                    if (i == 2) { //store timestamp
                        lastSpikedOMCArray[i][counter] = lastOMCTimeStamp;
                    }
                }
            }
            int dt = e.timestamp - lastCheckTimestampUs;
            if (dt < 0) {
                lastCheckTimestampUs = e.timestamp;
                DrawFire = false;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                DrawFire = false;
                lastCheckTimestampUs = e.timestamp;
            }
        }
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBlendEquation(GL2.GL_FUNC_ADD);
        gl.glPushMatrix();
        // Green background to show where the inhibition is
        gl.glPushMatrix();
        gl.glColor4f(0, 1, 0, 0.1f);
        gl.glRectf((0 << subsample),
                (0 << subsample),
                ((nxmax) << subsample),
                ((nymax) << subsample));
        gl.glPopMatrix();
        // Red squares to show where the cells are
        for (int omcx = 1; omcx < (nxmax - 1); omcx += 4) {// 4 corners
            for (int omcy = 1; omcy < (nymax - 1); omcy += 4) {
                gl.glPushMatrix();
                gl.glColor4f(1, 0, 0, 0.1f); //4 side centers
                gl.glRectf((omcx << subsample),
                        (omcy << subsample),
                        (omcx + 2 << subsample),
                        (omcy + 2 << subsample));
                gl.glPopMatrix();
            }
        }
        gl.glPushMatrix();// central center
        gl.glColor4f(1, 0, 0, 0.1f);
        gl.glRectf((3 << subsample),
                (3 << subsample),
                (3 + 2 << subsample),
                (3 + 2 << subsample));
        gl.glPopMatrix();

        if (counter < 2 - 1) {
            counter++;
        } else {
            counter = 0;
        }
        if (counter == 0) {
            lastIndex = 2 - 1;
        } else {
            lastIndex = counter - 1;
        }

        if (DrawFire) {
            // Render all outputs
            gl.glPushMatrix();
            gl.glColor4f(1, 0, 0, 1f); // Violet outputs of OMCs
            gl.glRectf((lastSpikedOMC[0] << subsample),
                    (lastSpikedOMC[1] << subsample),
                    (lastSpikedOMC[0] + 2 << subsample),
                    (lastSpikedOMC[1] + 2 << subsample));
            gl.glPopMatrix();
        }

        if (lastCheckTimestampUs - lastOMCTimeStamp > 1000 * 50) {//reset if long wait
            for (int index = 0; index < 2; index++) {
                for (int i = 0; i < 3; i++) {
                    if (i == 0) { //store x
                        lastSpikedOMCArray[i][index] = lastSpikedOMC[0];
                    }
                    if (i == 1) { //store y
                        lastSpikedOMCArray[i][index] = lastSpikedOMC[1];
                    }
                    if (i == 2) { //store timestamp
                        lastSpikedOMCArray[i][index] = lastOMCTimeStamp;
                    }
                }
            }
        }
        if (!(lastSpikedOMC[0] == lastSpikedOMCArray[0][counterP])) {
            //Render Arrow
            // motion vector points in direction of motion, *from* dir value (minus sign) which points in direction from prevous event
            gl.glPushMatrix();
            gl.glColor4f(1, 1, 0, 1f); // Violet outputs of OMCs
            gl.glLineWidth(10);
            DrawGL.drawVector(gl, (lastSpikedOMCArray[0][counterP] + 1) << subsample,
                    lastSpikedOMCArray[1][counterP] + 1 << subsample,
                    ((lastSpikedOMC[0] + 1) << subsample) - ((lastSpikedOMCArray[0][counterP] + 1) << subsample),
                    ((lastSpikedOMC[1] + 1) << subsample) - ((lastSpikedOMCArray[1][counterP] + 1) << subsample),
                    1 << subsample, 1);
            gl.glPopMatrix();
        }
        counterP = counter;

        gl.glPopMatrix();
        gl.glPopMatrix();
    }

    @Override
    public void resetFilter() {
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCArray = new int[3][2];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 3; i++) {
                if (i == 0) { //store x
                    lastSpikedOMCArray[i][j] = lastSpikedOMC[0];
                }
                if (i == 1) { //store y
                    lastSpikedOMCArray[i][j] = lastSpikedOMC[1];
                }
                if (i == 2) { //store timestamp
                    lastSpikedOMCArray[i][j] = lastOMCTimeStamp;
                }
            }
        }
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
}
