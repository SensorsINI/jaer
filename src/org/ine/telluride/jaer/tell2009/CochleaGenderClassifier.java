/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2009;
import net.sf.jaer.eventprocessing.filter.ISIHistogrammer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
/**
 * Extends ISIHistogrammer to use ISI histograms for gender classification based on pre-learned histograms.
 * @author tobi, shih-chii, nima, telluride 2009
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Extends ISIHistogrammer to use ISI histograms for gender classification based on pre-learned histograms.")
public class CochleaGenderClassifier extends ISIHistogrammer implements FrameAnnotater{
    private float getGenderThreshold = getPrefs().getFloat("CochleaGenderClassifier.threshold",1f);
    public enum Gender{
        Male, Female, Unknown
    };
    private Gender gender = Gender.Unknown;
    private static float[] weights = { -0.080072f,-0.137934f,-0.121719f,-0.037092f,0.055964f,
        0.211480f,0.189628f,-0.007691f,0.123760f,0.233077f,0.213994f,0.103129f,
        0.157239f,0.273423f,0.163444f,0.225852f,0.130565f,-0.029779f,
        -0.054216f,-0.132276f,-0.131664f,-0.183801f,0.023918f,0.007003f,
        -0.138904f,-0.235957f,-0.203452f,-0.196405f,-0.231883f,-0.186246f,-0.180011f,-0.108253f,-0.256415f,-0.247059f,-0.157670f,
        -0.073598f,-0.108831f,-0.115181f,-0.141374f,-0.097205f,-0.093439f,-0.067143f,-0.030989f,-0.034771f,-0.023676f,-0.024185f,
        -0.065150f,-0.023104f,-0.025864f,-0.019137f
    };
    private TextRenderer titleRenderer;
    /** The area to draw the title. */
    private Rectangle titleArea;
    private final float SCALE = 100;

    public CochleaGenderClassifier (AEChip chip){
        super(chip);
        setNBins(weights.length);
        setMaxIsiUs(9000);
        setMinIsiUs(3000);
        setDirection(Direction.XtimesYDirection);
        titleRenderer = new TextRenderer(new Font("Helvetica",Font.PLAIN,48));
        Rectangle2D bounds = titleRenderer.getBounds(Gender.Unknown.toString());
        titleArea = new Rectangle((int)bounds.getWidth(),(int)bounds.getHeight());
        setPropertyTooltip("Gender params","genderThreshold","threshold for abs(dot product) to classify as (female) speaker. If abs(dot)<genderThreshold then sex is Unknown.");
    }
    private volatile float genderDotProduct = 0;

    @Override
    public synchronized EventPacket<?> filterPacket (EventPacket<?> in){
        super.filterPacket(in);
        genderDotProduct = computeDotProduct();
        if ( genderDotProduct > (getGenderThreshold / SCALE) ){
            gender = Gender.Female;
//            System.out.println("MALE");
        } else if ( genderDotProduct < (-getGenderThreshold / SCALE) ){
            gender = Gender.Male;
//            System.out.println("FEMALE");
        } else{
            gender = Gender.Unknown;
        }
        return in;
    }

    private float computeDotProduct (){
        int[] bins = getBins();
        if ( bins.length != weights.length ){
            log.warning("bins.length!=weights.length");
            return 0;
        }
        float[] normBins = new float[ bins.length ];
        int sum = 0;
        for ( int binval:bins ){
            sum += binval;
        }
        for ( int i = 0 ; i < bins.length ; i++ ){
            normBins[i] = (float)bins[i] / sum;
        }
        float dot = 0;
        for ( int i = 0 ; i < bins.length ; i++ ){
            dot += normBins[i] * weights[i];
        }
        return dot;
    }

    /**
     * @return the getGenderThreshold
     */
    public float getGetGenderThreshold (){
        return getGenderThreshold;
    }

    /**
     * @param getGenderThreshold the getGenderThreshold to set
     */
    public void setGetGenderThreshold (float getGenderThreshold){
        float old = this.getGenderThreshold;
        this.getGenderThreshold = getGenderThreshold;
        getPrefs().putFloat("CochleaGenderClassifier.threshold",getGenderThreshold);
        getSupport().firePropertyChange("threshold",old,this.getGenderThreshold);
    }

    private GLUT glut = new GLUT();

    @Override
	public void annotate (GLAutoDrawable drawable){
        GL2 gl = drawable.getGL().getGL2();
//        gl.glColor3f(1,1,1);
//        gl.glRasterPos3f(10,10,0);
//        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,gender.toString());
        // title
        titleRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        switch (gender) {
            case Male:
                titleRenderer.setColor(Color.RED);
                break;
            case Female:
                titleRenderer.setColor(Color.GREEN);
                break;
            case Unknown:
                titleRenderer.setColor(Color.WHITE);
                break;
        }
        titleRenderer.draw(String.format("%10s %-6.2f", gender.toString(), SCALE * genderDotProduct), titleArea.width / 2, titleArea.height / 2);
        titleRenderer.endRendering();
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glTranslatef(drawable.getSurfaceWidth() / 2,drawable.getSurfaceHeight() / 2,0);
        switch ( gender ){
            case Male:
                gl.glColor3f(1,0,0);
                break;
            case Female:
                gl.glColor3f(0,1,0);
                break;
            case Unknown:
                gl.glColor3f(1,1,1);
                break;
        }
        float w = drawable.getSurfaceWidth() * genderDotProduct*5;
        gl.glRectf(0,-10,w,10);

        gl.glPopMatrix();
    }

    /**
     * @return the gender
     */
    public Gender getGender() {
        return gender;
    }

    /**
     * @return the genderDotProduct
     */
    public float getGenderDotProduct() {
        return genderDotProduct;
    }


}
