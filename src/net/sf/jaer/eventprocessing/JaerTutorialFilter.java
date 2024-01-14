package net.sf.jaer.eventprocessing;


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 * example of a lowpass average event location
 * @author Tobi
 */
@Description("example of a lowpass average event location")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class JaerTutorialFilter extends EventFilter2D implements FrameAnnotater {

    private float xmean=0,ymean=0;
    protected float mixingFactor=getFloat("mixingFactor", 0.01f);
    
    public JaerTutorialFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("mixingFactor", "IIR lowpass update rate");
    }
    
    

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        for(BasicEvent e:in){
            xmean=(1-mixingFactor)*xmean+mixingFactor*e.x;
            ymean=(1-mixingFactor)*ymean+mixingFactor*e.y;
        }
//        log.info(String.format("x,y means=(%.1f,%.1f)",xmean,ymean));
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
         GL2 gl=drawable.getGL().getGL2();
        // already in chip pixel context with LL corner =0,0
        
        gl.glPushMatrix();
        gl.glColor3f(0,0,1);
        gl.glLineWidth(4);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2d(xmean-10,ymean-10);
        gl.glVertex2d(xmean+10,ymean-10);
        gl.glVertex2d(xmean+10,ymean+10);
        gl.glVertex2d(xmean-10,ymean+10);
        gl.glEnd();
        gl.glPopMatrix();
       
    }

    /**
     * @return the mixingFactor
     */
    public float getMixingFactor() {
        return mixingFactor;
    }

    /**
     * @param mixingFactor the mixingFactor to set
     */
    public void setMixingFactor(float mixingFactor) {
        this.mixingFactor = mixingFactor;
        putFloat("mixingFactor",mixingFactor);
    }
    
}
