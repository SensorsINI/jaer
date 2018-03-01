/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.erbmlearn;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2D;

import org.apache.commons.lang3.ArrayUtils;
import org.jblas.DoubleMatrix;

import ch.unizh.ini.JEvtLearn.ERBM;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;

import com.jogamp.opengl.util.gl2.GLUT;
/**
 *
 * @author danny
 */
@Description("Class for online learning of event-based RBMs") // this annotation is used for tooltip to this class in the chooser.
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ERBMLearnFilter extends EventFilter2D {

    ERBM erbm;
    boolean qs;
    int x_size = 28;
    int y_size = 28;

    int vis_size = x_size * y_size;
    int h_size = 100;

    boolean show_weights = false;
    boolean displayNeuronStatistics = true;
    float wMin = -2;
    float wMax =  2;

    float vis_tau = 0.1f;
    float learn_rate = 0.001f;

    float thrVisMin = -2;
    float thrVisMax =  2;
    float thrHidMin = -2;
    float thrHidMax =  2;

    float inv_decay = 0.001f / 100.0f;

    // Display Variables
    private GLU glu = null;                 // OpenGL Utilities
    private JFrame weightFrame = null;      // Frame displaying neuron weight matrix
    private GLCanvas weightCanvas = null;   // Canvas on which weightFrame is drawn

    boolean show_viz = false;
    private JFrame vizFrame = null;      // Frame displaying neuron weight matrix
    private GLCanvas vizCanvas = null;   // Canvas on which weightFrame is drawn

    boolean show_acc = false;
    private JFrame accFrame = null;      // Frame displaying neuron weight matrix
    private GLCanvas accCanvas = null;   // Canvas on which weightFrame is drawn

    // Accuracy
    int numAccPoints = 100;
    float [] accuracy_log = new float[numAccPoints];
    DoubleMatrix slow_recon;
    DoubleMatrix slow_orig;
    float acc_tau = 4;

    public ERBMLearnFilter(AEChip chip) {
        super(chip);
        erbm = new ERBM(vis_size, h_size);
        slow_recon = new DoubleMatrix(vis_size);
        slow_orig = new DoubleMatrix(vis_size);
    }

    /** The main filtering method. It computes the mean location using an event-driven update of location and then
     * filters out events that are outside this location by more than the radius.
     * @param in input packet
     * @return output packet
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        List<Integer> layers = new ArrayList<Integer>();
        List<Double> times = new ArrayList<Double>();
        List<Integer> addrs = new ArrayList<Integer>();

        for (BasicEvent o : in) { // iterate over all events in input packet
            if( ((PolarityEvent) o).polarity == Polarity.On){
                times.add(o.timestamp / 1e6);
                layers.add(0);
                addrs.add((Math.round(((float) o.y / (chip.getSizeY()-1)) * (y_size-1)) * x_size) +
                            Math.round(((float) o.x / (chip.getSizeX()-1)) * (x_size-1)));
            }
        }

        if(times.size() > 0){
            erbm.processSpikesUntil(times.get(times.size() - 1));
            erbm.addSpikes(ArrayUtils.toPrimitive(times.toArray(new Double[times.size()])),
                           ArrayUtils.toPrimitive(layers.toArray(new Integer[layers.size()])),
                           ArrayUtils.toPrimitive(addrs.toArray(new Integer[addrs.size()])));
        }

        // Draw Neuron Weight Matrix
        if(show_weights){
            checkWeightFrame();
            weightCanvas.repaint();
        }

        // Draw Neuron Weight Matrix
        if(show_viz){
            checkVizFrame();
            vizCanvas.repaint();
        }
        if(show_acc){
            checkAccFrame();
            accCanvas.repaint();
        }

        // do inverse decay
        erbm.weights.addi(inv_decay);

        return in; // return the output packet
    }

    /** called when filter is reset
     *
     */
    @Override
    public void resetFilter() {
        erbm = new ERBM(vis_size, h_size);
        slow_recon = new DoubleMatrix(vis_size);
        slow_orig = new DoubleMatrix(vis_size);

        erbm.weights = DoubleMatrix.rand(vis_size, h_size).muli(0.1f);
        //float char_tau = 0.01f;

        setSTDPWin(getSTDPWin());
        setLearnRate(getLearnRate());
        setThrLearnRate(getThrLearnRate());
        setTRefrac(getTRefrac());
        setTau(getTau());
        setReconTau(getReconTau());
    }

    @Override
    public void initFilter() {

    }

    void checkWeightFrame() {
        if ((weightFrame == null) || ((weightFrame != null) && !weightFrame.isVisible())) {
			createWeightFrame();
		}
    }

    void hideWeightFrame(){
        if(weightFrame!=null) {
			weightFrame.setVisible(false);
		}
    }


    void createWeightFrame() {
        // Initializes weightFrame
        weightFrame = new JFrame("Weight Matrix");
        weightFrame.setPreferredSize(new Dimension(400, 400));
        // Creates drawing canvas
        weightCanvas = new GLCanvas();
        // Adds listeners to canvas so that it will be updated as needed
        weightCanvas.addGLEventListener(new GLEventListener() {
            // Called by the drawable immediately after the OpenGL context is initialized
            @Override
            public void init(GLAutoDrawable drawable) {

            }

            // Called by the drawable to initiate OpenGL rendering by the client
            // Used to draw and update canvas
            @Override
            synchronized public void display(GLAutoDrawable drawable) {
                if (erbm.weights == null) {
					return;
				}

                // Prepare drawing canvas
                int border_width  =  2;
                int weightPadding = 20;
                int neuronPadding = 5;
                int neuronsPerRow = (int) Math.floor(Math.sqrt(h_size));
                int neuronsPerColumn = (int) Math.floor(Math.sqrt(h_size));
                int xPixelsPerNeuron = x_size + neuronPadding;
                int yPixelsPerNeuron = y_size + neuronPadding;
                int xPixelsPerRow = (xPixelsPerNeuron * neuronsPerRow) - neuronPadding;
                int yPixelsPerRow = yPixelsPerNeuron;
                int xPixelsPerColumn = xPixelsPerNeuron;
                int yPixelsPerColumn = (yPixelsPerNeuron * neuronsPerRow) - neuronPadding;
                int yThreshStart = yPixelsPerColumn + weightPadding;

                int totX = xPixelsPerRow;
                int totY = yThreshStart + y_size + neuronPadding;

                // Draw in canvas
                GL2 gl = drawable.getGL().getGL2();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getSurfaceWidth() / (float) totX,
                            drawable.getSurfaceHeight() / (float) totY, 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);

                int rowOffset;
                int columnOffset;

                //float[][] weights = (float [][]) erbm.weights.toArray2();
                wMax = (float) erbm.weights.max();
                wMin = (float) erbm.weights.min();
                // Draw all Neurons
                for (int c=0; c < neuronsPerColumn; c++) {
                    columnOffset = c*yPixelsPerNeuron;
                    for (int r=0; r < neuronsPerRow; r++) {
                        // Adjust x Group Offset
                        rowOffset = r*xPixelsPerNeuron;

                        // Draw weights for this neuron
                        for (int x=0; x < x_size; x++) {
                            for (int y=0; y < y_size; y++) {
                                float w = ((float) erbm.weights.get((y*x_size) + x, (c*neuronsPerRow) + r) - wMin) / (wMax - wMin);
                                gl.glColor3f(w, w, w);
                                gl.glRectf(rowOffset+x, columnOffset+y,
                                           rowOffset+x+1, columnOffset+y+1);
                            } // END LOOP - Y
                        } // END LOOP - X

                        float border = (float) Math.exp( (float) -(erbm.sys_time - erbm.last_spiked[1].get((c*neuronsPerRow) + r)) / vis_tau);
                        gl.glColor3f(0, 0, border);

                        gl.glLineWidth(border_width);
                        gl.glBegin(GL.GL_LINE_STRIP);
                        gl.glVertex2d(rowOffset-1, columnOffset-1);
                        gl.glVertex2d(rowOffset-1, columnOffset+1+y_size);
                        gl.glVertex2d(rowOffset+1+x_size, columnOffset+1+y_size);
                        gl.glVertex2d(rowOffset+1+x_size, columnOffset-1);
                        gl.glVertex2d(rowOffset-1, columnOffset-1);
                        gl.glEnd();
                    } // END LOOP - Row
                } // END LOOP - Column

                // Display Neuron Statistics - Mean, STD, Min, Max
                if (displayNeuronStatistics == true) {
                    final int font = GLUT.BITMAP_HELVETICA_12;
                    GLUT glut = chip.getCanvas().getGlut();
                    gl.glColor3f(1, 1, 1);
                    // Neuron info
                    gl.glRasterPos3f(0, yPixelsPerColumn + neuronPadding, 0);
                    glut.glutBitmapString(font, String.format("M %.2f | wMax: %.2f | wMin: %2f | Time: %.2f | LastSpike[1]: %.2f | LastSpike[3]: %.2f",
                            erbm.weights.mean(), wMax, wMin, erbm.last_update[1],
                            erbm.last_spiked[1].max(), erbm.last_spiked[3].max()));
                }

                // Draw thresholds for visible
                thrVisMax = (float) erbm.thr[0].max();
                thrVisMin = (float) erbm.thr[0].min();
                for (int x=0; x < x_size; x++) {
                    for (int y=0; y < y_size; y++) {
                        float thr = ((float) erbm.thr[0].get((y*x_size) +x) - thrVisMin) / (thrVisMax - thrVisMin);
                        gl.glColor3f(thr, thr, thr);
                        gl.glRectf(x, yThreshStart+y,
                                   x+1, yThreshStart+y+1);
                    } // END LOOP - Y
                } // END LOOP - X

                // Draw thresholds for hidden
                int dim_hid = (int) Math.round(Math.sqrt(h_size));
                float xHScale = x_size / dim_hid;
                float yHScale = x_size / dim_hid;
                thrHidMax = (float) erbm.thr[1].max();
                thrHidMin = (float) erbm.thr[1].min();
                for (int x=0; x < dim_hid; x++) {
                    for (int y=0; y < dim_hid; y++) {
                        float thr = ((float) erbm.thr[1].get((y*dim_hid) +x) - thrHidMin) / (thrHidMax - thrHidMin);
                        gl.glColor3f(thr, thr, thr);
                        gl.glRectf(neuronPadding+x_size+(x*xHScale),   yThreshStart+(y*yHScale),
                                   neuronPadding+x_size+((x+1)*xHScale), yThreshStart+((y+1)*yHScale));
                    } // END LOOP - Y
                } // END LOOP - X

                // Display Neuron Statistics - Mean, STD, Min, Max
                if (displayNeuronStatistics == true) {
                    final int font = GLUT.BITMAP_HELVETICA_12;
                    GLUT glut = chip.getCanvas().getGlut();
                    gl.glColor3f(1, 1, 1);
                    // Neuron info
                    gl.glRasterPos3f(0, yThreshStart + y_size + neuronPadding, 0);
                    glut.glutBitmapString(font, String.format("thrVisMax: %.2f | thrVisMin: %2f | thrHidMax: %.2f | thrHidMin: %2f | Spikes[0]: %,d | Spikes[1]: %,d | Spikes[2]: %,d | Spikes[3]: %,d",
                            thrVisMax, thrVisMin, thrHidMax, thrHidMin,
                            erbm.spike_count[0], erbm.spike_count[1], erbm.spike_count[2], erbm.spike_count[3]));
                }

                // Log error if there is any in OpenGL
                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    if (glu == null) {
						glu = new GLU();
					}
                    log.log(Level.WARNING, "GL error number {0} {1}", new Object[]{error, glu.gluErrorString(error)});
                } // END IF
            } // END METHOD - Display

            // Called by the drawable during the first repaint after the component has been resized
            // Adds a border to canvas by adding perspective to it and then flattening out image
            @Override
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL2 gl = drawable.getGL().getGL2();
                final int border = 10;
                gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glOrtho(-border, drawable.getSurfaceWidth() + border, -border, drawable.getSurfaceHeight() + border, 10000, -10000);
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);
            } // END METHOD

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
        }); // END SCOPE - GLEventListener

        // Add weightCanvas to weightFrame
        weightFrame.getContentPane().add(weightCanvas);
        // Causes window to be sized to fit the preferred size and layout of its subcomponents
        weightFrame.pack();
        weightFrame.setVisible(true);
    } // END METHOD
    /**
     * Called when filter is turned off
     * makes sure weightFrame gets turned off
     */

    void checkVizFrame() {
        if ((vizFrame == null) || ((vizFrame != null) && !vizFrame.isVisible())) {
			createVizFrame();
		}
    }

    void hideVizFrame(){
        if(vizFrame!=null) {
			vizFrame.setVisible(false);
		}
    }


    void createVizFrame() {
        // Initializes weightFrame
        vizFrame = new JFrame("Reconstruction Visualization");
        vizFrame.setPreferredSize(new Dimension(800, 400));
        // Creates drawing canvas
        vizCanvas = new GLCanvas();
        // Adds listeners to canvas so that it will be updated as needed
        vizCanvas.addGLEventListener(new GLEventListener() {
            // Called by the drawable immediately after the OpenGL context is initialized
            @Override
            public void init(GLAutoDrawable drawable) {

            }

            // Called by the drawable to initiate OpenGL rendering by the client
            // Used to draw and update canvas
            @Override
            synchronized public void display(GLAutoDrawable drawable) {
                if (erbm.recon[2] == null) {
					return;
				}

                // Prepare sizing
                int neuronPadding = 5;
                int totX = (2 * x_size) + neuronPadding;
                int totY = y_size;

                // Draw in canvas
                GL2 gl = drawable.getGL().getGL2();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getSurfaceWidth() / (float) totX,
                            drawable.getSurfaceHeight() / (float) totY, 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);

                // Draw reconstruction
                int recon_layer = 0;
                float rVMax = (float) erbm.recon[recon_layer].max();
                float rVMin = (float) erbm.recon[recon_layer].min();
                for (int x=0; x < x_size; x++) {
                    for (int y=0; y < y_size; y++) {
                        float r = ((float) erbm.recon[recon_layer].get((y*x_size)+x) - rVMin) / (rVMax - rVMin);
                        gl.glColor3f(r, r/1.5f, r/1.5f);
                        gl.glRectf(x, y,
                                   x+1, y+1);
                    } // END LOOP - Y
                } // END LOOP - X

                int xReconStart = x_size + neuronPadding;
                int yReconStart = 0;

                // Draw reconstruction
                recon_layer = 2;
                float rHMax = (float) erbm.recon[recon_layer].max();
                float rHMin = (float) erbm.recon[recon_layer].min();
                for (int x=0; x < x_size; x++) {
                    for (int y=0; y < y_size; y++) {
                        float r = ((float) erbm.recon[recon_layer].get((y*x_size)+x) - rHMin) / (rHMax - rHMin);
                        gl.glColor3f(r/1.5f, r/1.5f, r);
                        gl.glRectf(xReconStart+x, yReconStart+y,
                                   xReconStart+x+1, yReconStart+y+1);
                    } // END LOOP - Y
                } // END LOOP - X


                // Display Neuron Statistics - Mean, STD, Min, Max
                if (displayNeuronStatistics == true) {
                    final int font = GLUT.BITMAP_HELVETICA_12;
                    GLUT glut = chip.getCanvas().getGlut();
                    gl.glColor3f(1, 1, 1);
                    // Neuron info
                    gl.glRasterPos3f(0, 0, 0);
                    glut.glutBitmapString(font, String.format("rVMin: %.2f | rVMax: %.2f | rHMin: %.2f | rHMax: %.2f",
                            rVMin, rVMax, rHMin, rHMax));
                }

                // Log error if there is any in OpenGL
                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    if (glu == null) {
						glu = new GLU();
					}
                    log.log(Level.WARNING, "GL error number {0} {1}", new Object[]{error, glu.gluErrorString(error)});
                } // END IF
            } // END METHOD - Display

            // Called by the drawable during the first repaint after the component has been resized
            // Adds a border to canvas by adding perspective to it and then flattening out image
            @Override
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL2 gl = drawable.getGL().getGL2();
                final int border = 10;
                gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glOrtho(-border, drawable.getSurfaceWidth() + border, -border, drawable.getSurfaceHeight() + border, 10000, -10000);
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);
            } // END METHOD

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
        }); // END SCOPE - GLEventListener

        // Add weightCanvas to weightFrame
        vizFrame.getContentPane().add(vizCanvas);
        // Causes window to be sized to fit the preferred size and layout of its subcomponents
        vizFrame.pack();
        vizFrame.setVisible(true);
    } // END METHOD


    void checkAccFrame() {
        if ((accFrame == null) || ((accFrame != null) && !accFrame.isVisible())) {
			createAccFrame();
		}
    }

    void hideAccFrame(){
        if(accFrame!=null) {
			accFrame.setVisible(false);
		}
    }


    void createAccFrame() {
        // Initializes weightFrame
        accFrame = new JFrame("Accuracy Visualization");
        accFrame.setPreferredSize(new Dimension(800, 400));
        // Creates drawing canvas
        accCanvas = new GLCanvas();
        // Adds listeners to canvas so that it will be updated as needed
        accCanvas.addGLEventListener(new GLEventListener() {
            // Called by the drawable immediately after the OpenGL context is initialized
            @Override
            public void init(GLAutoDrawable drawable) {

            }

            // Called by the drawable to initiate OpenGL rendering by the client
            // Used to draw and update canvas
            @Override
            synchronized public void display(GLAutoDrawable drawable) {
                if (erbm.recon[2] == null) {
					return;
				}

                // Prepare sizing
                int totX = numAccPoints;
                int totY = 100;

                // Update slow-pass reconstruction
                slow_orig.muli(1-(1/acc_tau)).addi(erbm.recon[0].mul(1/acc_tau));
                slow_recon.muli(1-(1/acc_tau)).addi(erbm.recon[2].mul(1/acc_tau));

                // Update accuracy log - TERRIBLE code
                float [] new_log = new float[numAccPoints];
                for(int i=0; i<(numAccPoints-1); i++){
                    new_log[i] = accuracy_log[i+1];
                }
                new_log[numAccPoints-1] = (float) slow_recon.div(slow_recon.norm2()).dot(slow_orig.div(slow_orig.norm2()));
                accuracy_log = new_log;

                // Draw in canvas
                GL2 gl = drawable.getGL().getGL2();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getSurfaceWidth() / (float) totX,
                            drawable.getSurfaceHeight() / (float) totY, 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0.2f, 0.2f, 0.2f, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);

                gl.glLineWidth(2.5f);
                gl.glColor3f(1.0f, 0.0f, 0.0f);
                for(int i=1; i<numAccPoints; i++){
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(i-1, accuracy_log[i-1]*100);
                    gl.glVertex2f(i, accuracy_log[i]*100);
                    gl.glEnd();
                }


                // Display Accuracy
                final int font = GLUT.BITMAP_HELVETICA_12;
                GLUT glut = chip.getCanvas().getGlut();
                gl.glColor3f(1, 1, 1);
                // Y-Axis Labels
                float[] labels = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
                for (float label : labels) {
                    gl.glRasterPos3f(0, label, 0);
                    glut.glutBitmapString(font, String.format("%.0f", label));
                }

                gl.glRasterPos3f(100, 50, 0);
                glut.glutBitmapString(font, String.format("Current: %f", accuracy_log[numAccPoints-1]));


                // Log error if there is any in OpenGL
                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    if (glu == null) {
						glu = new GLU();
					}
                    log.log(Level.WARNING, "GL error number {0} {1}", new Object[]{error, glu.gluErrorString(error)});
                } // END IF
            } // END METHOD - Display

            // Called by the drawable during the first repaint after the component has been resized
            // Adds a border to canvas by adding perspective to it and then flattening out image
            @Override
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL2 gl = drawable.getGL().getGL2();
                final int border = 10;
                gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glOrtho(-border, drawable.getSurfaceWidth() + border, -border, drawable.getSurfaceHeight() + border, 10000, -10000);
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);
            } // END METHOD

			@Override
			public void dispose(GLAutoDrawable arg0) {
				// TODO Auto-generated method stub

			}
        }); // END SCOPE - GLEventListener

        // Add weightCanvas to weightFrame
        accFrame.getContentPane().add(accCanvas);
        // Causes window to be sized to fit the preferred size and layout of its subcomponents
        accFrame.pack();
        accFrame.setVisible(true);
    } // END METHOD

    @Override
    public synchronized void cleanup() {
        if(weightFrame!=null) {
			weightFrame.dispose();
		}
        if(vizFrame!=null) {
			vizFrame.dispose();
		}
        if(accFrame!=null) {
			accFrame.dispose();
		}
    } // END METHOD

    /**
     * Resets the filter
     * Hides weightFrame if filter is not enabled
     * @param yes true to reset
     */
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if(!isFilterEnabled()){
            hideWeightFrame();
            hideVizFrame();
            hideAccFrame();
        }
    } // END METHOD


    public void setShowWeights(final boolean show_weights) {
        getPrefs().putBoolean("ERBMLearn.showWeights", show_weights);
        getSupport().firePropertyChange("showWeights", this.show_weights, show_weights);
        this.show_weights = show_weights;
    }
    public boolean getShowWeights(){
        return getPrefs().getBoolean("ERBMLearn.showWeights", true);
    }

    public void setShowAcc(final boolean show_acc) {
        getPrefs().putBoolean("ERBMLearn.showAcc", show_acc);
        getSupport().firePropertyChange("show_acc", this.show_acc, show_acc);
        this.show_acc = show_acc;
    }
    public boolean getShowAcc(){
        return getPrefs().getBoolean("ERBMLearn.show_acc", true);
    }

    public void setShowViz(final boolean show_viz) {
        getPrefs().putBoolean("ERBMLearn.showViz", show_viz);
        getSupport().firePropertyChange("show_viz", this.show_viz, show_viz);
        this.show_viz = show_viz;
    }
    public boolean getShowViz(){
        return getPrefs().getBoolean("ERBMLearn.show_vis", true);
    }

    public void setTRefrac(final float t_refrac) {
        getPrefs().putFloat("ERBMLearn.t_refrac", t_refrac);
        getSupport().firePropertyChange("t_refrac", erbm.t_refrac, t_refrac);
        erbm.t_refrac = t_refrac;
    }
    public float getTRefrac(){
        return getPrefs().getFloat("ERBMLearn.t_refrac", 0.001f);
    }

    public void setVisTau(final float vis_tau) {
        getPrefs().putFloat("ERBMLearn.vis_tau", vis_tau);
        getSupport().firePropertyChange("vis_tau", this.vis_tau, vis_tau);
        this.vis_tau = vis_tau;
    }
    public float getVisTau(){
        return getPrefs().getFloat("ERBMLearn.vis_tau", 0.1f);
    }

    public void setLearnRate(final float learn_rate) {
        getPrefs().putFloat("ERBMLearn.learn_rate", learn_rate);
        getSupport().firePropertyChange("learn_rate", erbm.eta, learn_rate);
        erbm.eta = learn_rate;
    }
    public float getLearnRate(){
        return getPrefs().getFloat("ERBMLearn.learn_rate", 0.001f);
    }

    public void setThrLearnRate(final float thr_learn_rate) {
        getPrefs().putFloat("ERBMLearn.thr_learn_rate", thr_learn_rate);
        getSupport().firePropertyChange("thr_learn_rate", erbm.thresh_eta, thr_learn_rate);
        erbm.thresh_eta = thr_learn_rate;
    }
    public float getThrLearnRate(){
        return getPrefs().getFloat("ERBMLearn.thr_learn_rate", 0.0f);
    }

    public void setTau(final float tau) {
        getPrefs().putFloat("ERBMLearn.tau", tau);
        getSupport().firePropertyChange("tau", erbm.tau, tau);
        erbm.tau = tau;
    }
    public float getTau(){
        return getPrefs().getFloat("ERBMLearn.tau", 0.100f);
    }
    public void setReconTau(final float recon_tau) {
        getPrefs().putFloat("ERBMLearn.recon_tau", recon_tau);
        getSupport().firePropertyChange("recon_tau", erbm.recon_tau, recon_tau);
        erbm.recon_tau = recon_tau;
    }
    public float getReconTau(){
        return getPrefs().getFloat("ERBMLearn.recon_tau", 0.100f);
    }

    public void setSTDPWin(final float stdp_win) {
        getPrefs().putFloat("ERBMLearn.stdp_win", stdp_win);
        getSupport().firePropertyChange("stdp_win", erbm.stdp_lag, stdp_win);
        erbm.stdp_lag = stdp_win;
    }
    public float getSTDPWin(){
        return getPrefs().getFloat("ERBMLearn.stdp_win", 0.100f);
    }
    public void setInvDecay(final float inv_decay) {
        getPrefs().putFloat("ERBMLearn.inv_decay", inv_decay);
        getSupport().firePropertyChange("inv_decay", this.inv_decay, inv_decay);
        this.inv_decay = inv_decay;
    }
    public float getInvDecay(){
        return getPrefs().getFloat("ERBMLearn.inv_decay", 0.001f/ 100.0f);
    }


    /*
    public void setQuickSave(final boolean quicksave) {
        getPrefs().putBoolean("ERBMLearn.qs", quicksave);
        getSupport().firePropertyChange("qs", this.qs, quicksave);
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        try {
            String filename = "wts-" + strDate + ".dat";
            erbm.weights.save(filename);
        } catch (IOException ex) {
            Logger.getLogger(ERBMLearnFilter.class.getName()).log(Level.SEVERE, "Something happened with save", ex);
        }

    }
    public boolean getQuickSave(){
        return getPrefs().getBoolean("ERBMLearn.qs", false);
    }
    */
}