package eu.visualize.ini.retinamodel;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;

//-- Description -------------------------------------------------------------//
// Models multiple object motion cells that are excited by on or off activity
// within their classical receptive field but are inhibited by synchronous on or
// off activity in their extended RF, such as that caused by a saccadic eye
// movement. Also gives direction of movement of object
// @author diederik
@Description("Plots the output of the FPGA OMCs")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class OmcodFpgaVisualizer extends AbstractRetinaModelCell implements FrameAnnotater {

    public RosNodePublisher RosNodePublisher = new RosNodePublisher();
    private final LowpassFilter isiFilter = new LowpassFilter(10);
    private float subunitActivityBlobRadiusScale = getFloat("subunitActivityBlobRadiusScale", 0.022f);
    private float minSpikeRateHz = getFloat("minSpikeRate", 1f);
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
    private int[][] spikeRateHz; // spike rate
    private int[][] dtUSspikeArray;
    private int[][] timeStampSpikeArray;
    private int[][] lastTimeStampSpikeArray;
    final int subsample = 4;
    private boolean switchTo9OMCs = getBoolean("switchTo9OMCs", true);

    public OmcodFpgaVisualizer(AEChip chip) {
        super(chip);
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;
        this.spikeRateHz = new int[nxmax][nymax];
        this.dtUSspikeArray = new int[nxmax][nymax];
        this.timeStampSpikeArray = new int[nxmax][nymax];
        this.lastTimeStampSpikeArray = new int[nxmax][nymax];
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCArray = new int[3][2];
        final String use = "Parameters";
        setPropertyTooltip(use, "switchTo9OMCs", "Switch to 9 OMC design visualization");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        EventPacket temp = in;
        checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak.
        if (temp == null) {
            return in;
        }
        for (Object o : temp) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                DrawFire = false;
                continue;
            }
            // Check for firing cells and annotate
            if (((e.x == 31) && (e.y == 32)) || ((e.x == 95) && (e.y == 32)) || ((e.x == 31) && (e.y == 96))
                    || ((e.x == 95) && (e.y == 96)) || ((e.x == 63) && (e.y == 64)) || ((e.x == 63) && (e.y == 96))
                    || ((e.x == 63) && (e.y == 32)) || ((e.x == 31) && (e.y == 64)) || ((e.x == 95) && (e.y == 64))) {
                //Store all spiked cells
                if ((e.x == 31) && (e.y == 32)) {
                    lastSpikedOMC[0] = 1;
                    lastSpikedOMC[1] = 1;
                    lastOMCTimeStamp = e.timestamp;
                    DrawFire = true;
                }
                if ((e.x == 95) && (e.y == 32)) {
                    lastSpikedOMC[0] = 5;
                    lastSpikedOMC[1] = 1;
                    lastOMCTimeStamp = e.timestamp;
                    DrawFire = true;
                }
                if ((e.x == 31) && (e.y == 96)) {
                    lastSpikedOMC[0] = 1;
                    lastSpikedOMC[1] = 5;
                    lastOMCTimeStamp = e.timestamp;
                    DrawFire = true;
                }
                if ((e.x == 95) && (e.y == 96)) {
                    lastSpikedOMC[0] = 5;
                    lastSpikedOMC[1] = 5;
                    lastOMCTimeStamp = e.timestamp;
                    DrawFire = true;
                }
                if ((e.x == 63) && (e.y == 64)) {
                    lastSpikedOMC[0] = 3;
                    lastSpikedOMC[1] = 3;
                    lastOMCTimeStamp = e.timestamp;
                    DrawFire = true;
                }
                if (switchTo9OMCs) {
                    if ((e.x == 95) && (e.y == 64)) {
                        lastSpikedOMC[0] = 5;
                        lastSpikedOMC[1] = 3;
                        lastOMCTimeStamp = e.timestamp;
                        DrawFire = true;
                    }
                    if ((e.x == 31) && (e.y == 64)) {
                        lastSpikedOMC[0] = 1;
                        lastSpikedOMC[1] = 3;
                        lastOMCTimeStamp = e.timestamp;
                        DrawFire = true;
                    }
                    if ((e.x == 63) && (e.y == 96)) {
                        lastSpikedOMC[0] = 3;
                        lastSpikedOMC[1] = 5;
                        lastOMCTimeStamp = e.timestamp;
                        DrawFire = true;
                    }
                    if ((e.x == 63) && (e.y == 32)) {
                        lastSpikedOMC[0] = 3;
                        lastSpikedOMC[1] = 1;
                        lastOMCTimeStamp = e.timestamp;
                        DrawFire = true;
                    }
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
                if (!switchTo9OMCs) {
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
                spike(lastSpikedOMC[0], lastSpikedOMC[1], lastOMCTimeStamp);
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
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        //gl.glPushMatrix();
        // Green background to show where the inhibition is
        gl.glPushMatrix();
        gl.glColor4f(0, 1, 0, 0.1f);
        gl.glRectf((0 << subsample),
                (0 << subsample),
                ((nxmax) << subsample),
                ((nymax) << subsample));
        gl.glPopMatrix();
        // Red squares to show where the cells are

        if (spikeRateHz[lastSpikedOMC[0]][lastSpikedOMC[1]] >= minSpikeRateHz) {
            gl.glPushMatrix();
            gl.glColor4f(1, 0, 0, 0.5f); //4 side centers
            float radius = (chip.getMaxSize() * subunitActivityBlobRadiusScale * spikeRateHz[lastSpikedOMC[0]][lastSpikedOMC[1]]) / maxSpikeRateHz / 2;
            gl.glRectf((((lastSpikedOMC[0] + 1) << subsample) - radius),
                    (((lastSpikedOMC[1] + 1) << subsample) - radius),
                    (((lastSpikedOMC[0] + 1) << subsample) + radius),
                    (((lastSpikedOMC[1] + 1) << subsample) + radius));
            gl.glPopMatrix();
            //                        gl.glPushMatrix();
            // spikeRateHz[lastSpikedOMC[0]][lastSpikedOMC[1]] = 0;
        }

        if (counter < (2 - 1)) {
            counter++;
        } else {
            counter = 0;
        }
        if (counter == 0) {
            lastIndex = 2 - 1;
        } else {
            lastIndex = counter - 1;
        }

        if ((lastCheckTimestampUs - lastOMCTimeStamp) > (1000 * 50)) {//reset if long wait
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
        counterP = counter;
    }

    @Override
    public void resetFilter() {
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;
        this.spikeRateHz = new int[nxmax][nymax];
        this.dtUSspikeArray = new int[nxmax][nymax];
        this.timeStampSpikeArray = new int[nxmax][nymax];
        this.lastTimeStampSpikeArray = new int[nxmax][nymax];
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCArray = new int[3][2];
        isiFilter.reset();
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

    // @return the switchTo9OMCs
    public boolean isSwitchTo9OMCs() {
        return switchTo9OMCs;
    }

    // @param switchTo9OMCs the switchTo9OMCs to set
    public void setSwitchTo9OMCs(boolean switchTo9OMCs) {
        this.switchTo9OMCs = switchTo9OMCs;
        putBoolean("switchTo9OMCs", switchTo9OMCs);
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

//----------------------------------------------------------------------------//
//-- Spike method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
    void spike(int omcx, int omcy, int timestamp) {
        timeStampSpikeArray[omcx][omcy] = timestamp;
        dtUSspikeArray[omcx][omcy] = timeStampSpikeArray[omcx][omcy] - lastTimeStampSpikeArray[omcx][omcy];
        if (dtUSspikeArray[omcx][omcy] >= 0) {
            float avgIsiUs = isiFilter.filter(dtUSspikeArray[omcx][omcy], timeStampSpikeArray[omcx][omcy]);
            spikeRateHz[omcx][omcy] = Math.round(1e6f / avgIsiUs);
        }
        lastTimeStampSpikeArray[omcx][omcy] = timeStampSpikeArray[omcx][omcy];
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

    // @return the subunitActivityBlobRadiusScale
    public float getSubunitActivityBlobRadiusScale() {
        return subunitActivityBlobRadiusScale;
    }

    // @param subunitActivityBlobRadiusScale the subunitActivityBlobRadiusScale
    public void setSubunitActivityBlobRadiusScale(float subunitActivityBlobRadiusScale) {
        this.subunitActivityBlobRadiusScale = subunitActivityBlobRadiusScale;
        putFloat("subunitActivityBlobRadiusScale", subunitActivityBlobRadiusScale);
    }
//------------------------------------------------------------------------------

    // @return the minSpikeRateHz
    public float getMinSpikeRate() {
        return minSpikeRateHz;
    }

    // @param subunitActivityBlobRadiusScale the minSpikeRateHz
    public void setMinSpikeRateHz(float minSpikeRateHz) {
        this.minSpikeRateHz = minSpikeRateHz;
        putFloat("minSpikeRateHz", minSpikeRateHz);
    }
//------------------------------------------------------------------------------
}
