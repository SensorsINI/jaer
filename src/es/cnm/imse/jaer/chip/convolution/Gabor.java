/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package es.cnm.imse.jaer.chip.convolution;

import java.io.Serializable;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * Chip description for 9 gabor filter combinations with spike-based convolution system.
 *
 * The 9 Gabor filters were implemented
on a Virtex6. Each Gabor module includes a Convolution engine and a router (each described in
vhdl). A DVS recording is fed to the array and the outputs of each module are sent to an outside port.
Each module processes a 64x64 input space, and adds 4 extra bits to its output events to identify the
the filter. Thus, output events from the 3x3 Gabors form a 192x192 pixel space.
 *
 *
 * @author Carlos Zamarreño Ramos zamarreno@imse-cnm.csic.es, Bernabe Linares-Barranco
 */
public class Gabor extends AEChip implements Serializable {

    /** Creates a new instance of Tmpdiff128 */
    public Gabor() {
        setName("Gabor");
        setSizeX(192);
        setSizeY(192);
        setNumCellTypes(1);
        setEventClass(TypedEvent.class);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
        DisplayMethod m = new GaborDisplayMethod(this);
        getCanvas().addDisplayMethod(m);
        getCanvas().setDisplayMethod(m);
    }

    public class Extractor extends TypedEventExtractor implements java.io.Serializable {

        public Extractor(AEChip chip) {
            super(chip);
            setXmask((short) 0x00ff);
            setXshift((byte) 0);
            setYmask((short) 0xff00);
            setYshift((byte) 8);
            setTypemask((short) 0);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFliptype(false);
        }
    }

    public class GaborDisplayMethod extends ChipRendererDisplayMethod {

        public GaborDisplayMethod(AEChip chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            super.display(drawable);
            GL2 gl = drawable.getGL().getGL2();
            // draw boxes around gabor filters
            final int s = 64;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    rect(gl, r * s, c * s, s, s);
                }
            }
        }

        private void rect(GL2 gl, float x, float y, float w, float h) {
            gl.glLineWidth(1f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glVertex2f(x, y);
            gl.glVertex2f(x + w, y);
            gl.glVertex2f(x + w, y + h);
            gl.glVertex2f(x, y + h);
            gl.glEnd();
        }
    }
}
