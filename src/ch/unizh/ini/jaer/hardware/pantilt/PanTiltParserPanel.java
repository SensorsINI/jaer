/*
 * PanTiltParserPanel.java
 *
 * Created on Nov 30, 2011, 7:37:23 PM
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;

/**
 * a simple GUI for controlling a USB servo controller; the interface is
 * meant to be easy to use; you can directly specify the movement to be
 * executed in a simplistic command language specifying angles as well as
 * accelereation/decceleration profiles for easy construction of composite
 * sequences
 * 
 * @see ServoInterface
 *
 * @author andstein
 */
public class PanTiltParserPanel extends javax.swing.JPanel 
{
    
    static Logger log = Logger.getLogger(PanTiltParserPanel.class.getName());
    public final static int PAN_SERVO =0; // depends how you connect
    public final static int TILT_SERVO=1; // depends how you connect
    protected boolean outputTwice= false; // debug option for comparing different servos
    
    // also see protected classes AT END OF FILE !

    protected HashMap<String,String> defaultPrograms;
    protected ParserThread thread= null; // null means no thread running
    protected ServoInterface hwInterface= null;
    
    /** Creates new form PanTiltParserPanel */
    public PanTiltParserPanel() {
        initComponents();
        
        // initialize our default programs
        // sequence elemnents separated by spaces
        // one sequence consists of five values, separated by comma
        //   1) x : use "rectangular spherical coordinates"
        //   2) {r,q2,q3} : acceleration profile -- see ParserJunk
        //   3) duration for this sequence item (in seconds)
        //   4) delta-pan (in degrees)
        //   5) delta-tilt (in degrees)
        
        defaultPrograms= new HashMap<String,String>();
        defaultPrograms.put("calibrate",
                "x,r,1,+20,0 " +
                "x,r,1,-20,0 " +
                "x,r,1,0,+20 " +
                "x,r,1,0,-20 " +
                "");
        defaultPrograms.put("scan", 
                "x,r,.2,-30,-6 " + // move left/down
                            "x,q2,.5,+60,0 " + // 1st line
                "x,r,.1,0,+2 " + // next line
                            "x,q2,.5,-60,0 " + // 2nd line
                "x,r,.1,0,+2 " + // next line
                            "x,q2,.5,+60,0 " + // 3rd line
                "x,r,.1,0,+2 " + // next line
                            "x,q2,.5,-60,0 " + // 4th line
                "x,r,.1,0,+2 " + // next line
                            "x,q2,.5,+60,0 " + // 5th line
                "x,r,.1,0,+2 " + // next line
                            "x,q2,.5,-60,0 " + // 6th line
                "x,r,.1,0,+2 " + // next line
                "");
        defaultPrograms.put("slowscan", 
                "x,r,.2,-30,-18 " + // move left/down
                            "x,q2,2.5,+60,0 " + // 1st line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,-60,0 " + // 2nd line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,+60,0 " + // 3rd line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,-60,0 " + // 4th line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,+60,0 " + // 5th line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,-60,0 " + // 6th line
                "x,r,.3,0,+6 " + // next line
                            "x,q2,2.5,+60,0 " + // 7th line
                "");
        defaultPrograms.put("circle",
                "x,q3,.5,10,0 "+
                "p,q3,1,0,360 "+
                "x,q3,.5,-10,0 "+ // do one three-sixty
                "");

        defaultPrograms.put("envelopes", 
                "x,r,.6,-30,0 " + // start position (after relax)
                "x,r,.62,+60,0 " + // move rectangularly
                "x,r,.62,-60,0 " +
                "x,r,.62,+60,0 " +
                "x,r,.62,-60,0 " +
                "x,r,.62,+60,0 " +
                "x,r,.62,-60,0 " +
                "x,q3,.62,+60,0 " + // move quadratically
                "x,q3,.62,-60,0 " +
                "x,q3,.62,+60,0 " +
                "x,q3,.62,-60,0 " +
                "x,q3,.62,+60,0 " +
                "x,q3,.62,-60,0 " +
                "x,q3,.62,+60,0 " +
                "x,q3,.62,-60,0 " +
                "x,r,.62,+30,0 " + // relax
                "");
        defaultPrograms.put("test y", 
                "x,r,1,0,-10 " + // x-=10 in 1s, rectangular shape
                "x,r,2,0,+10 " + // x-=10 in 1s, rectangular shape
                "");
        defaultPrograms.put("spiral", 
                "p,r,10,+56,+720 " + // circle outwards (2 turns) in 10s
                "");
        defaultPrograms.put("<Custom>", null); // special string...
        
        // fill in default programs
        programCombo.removeAllItems();
        for(String name : defaultPrograms.keySet())
            programCombo.addItem(name);
        
        // fill in servo interfaces
        interfaceCombo.removeAllItems();
        interfaceCombo.addItem(""); // first entry means no interface selected
        for(int i=0; i<ServoInterfaceFactory.instance().getNumInterfacesAvailable(); i++)
            interfaceCombo.addItem( "Servo "+i );
        
         // autoselect first interface
        if (interfaceCombo.getItemCount() >1)
            interfaceCombo.setSelectedIndex(1);
    }
    
    protected float currentX,currentY; // in degrees
    
    public float convert(float degrees) {
        return .5f + degrees*.4f/45f;
    }

    public float unconvert(float f) {
        return (f-.5f)/.4f*45f;
    }

    public float getXDegrees() {
        return currentX; // assume 1st servo X direction
    }
    
    /**
     * sets the servo to the specified position (in degrees); will only update
     * internal variable if <code>hwInterface</code> not set.
     * 
     * @param degrees
     * @throws HardwareInterfaceException 
     */
    public void setXDegrees(float degrees) throws HardwareInterfaceException {
        currentX= degrees;
        if (hwInterface != null) {
            hwInterface.setServoValue(PAN_SERVO, convert(degrees)); // assume 1st servo X direction
            if (outputTwice)
                hwInterface.setServoValue(PAN_SERVO+2, convert(degrees)); // assume 1st servo X direction
        }
    }

    public float getYDegrees() {
        return currentY; // assume 2nd servo Y direction
    }
    
    /**
     * sets the servo to the specified position (in degrees); will only update
     * internal variable if <code>hwInterface</code> not set.
     * 
     * @param degrees
     * @throws HardwareInterfaceException 
     */
    public void setYDegrees(float degrees) throws HardwareInterfaceException {
        currentY= degrees;
        if (hwInterface != null) {
            hwInterface.setServoValue(TILT_SERVO, convert(degrees)); // assume 2nd servo Y direction
            if (outputTwice)
                hwInterface.setServoValue(TILT_SERVO+2, convert(degrees)); // assume 2nd servo Y direction
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        interfaceCombo = new javax.swing.JComboBox();
        programCombo = new javax.swing.JComboBox();
        startStopButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        textPane = new javax.swing.JTextPane();
        relaxButton = new javax.swing.JButton();
        msText = new javax.swing.JTextField();
        msLabel = new javax.swing.JLabel();
        flowButton = new javax.swing.JButton();

        interfaceCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        interfaceCombo.setToolTipText("choose hardware interface");
        interfaceCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interfaceComboActionPerformed(evt);
            }
        });

        programCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        programCombo.setToolTipText("choose predefined sequence");
        programCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                programComboActionPerformed(evt);
            }
        });

        startStopButton.setText("START");
        startStopButton.setToolTipText("start sequence (in TextArea)");
        startStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startStopButtonActionPerformed(evt);
            }
        });

        textPane.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        textPane.setToolTipText("see source code for how to construct sequences");
        textPane.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                textPaneKeyTyped(evt);
            }
        });
        jScrollPane1.setViewportView(textPane);

        relaxButton.setText("relax");
        relaxButton.setToolTipText("go to neutral position");
        relaxButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                relaxButtonActionPerformed(evt);
            }
        });

        msText.setFont(new java.awt.Font("Courier New", 0, 11));
        msText.setText("1.0");
        msText.setToolTipText("actually only wait specified value instead of 1ms (accounts for discrepancy of Thread.sleep)");
        msText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                msTextActionPerformed(evt);
            }
        });

        msLabel.setText("1ms=");

        flowButton.setForeground(new java.awt.Color(255, 0, 0));
        flowButton.setText("flow");
        flowButton.setToolTipText("follow optical flow (if available)");
        flowButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flowButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(relaxButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(startStopButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(programCombo, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(interfaceCombo, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(flowButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                            .addComponent(msLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(msText, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 259, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(13, 13, 13)
                        .addComponent(interfaceCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(programCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(startStopButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(relaxButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(msLabel)
                            .addComponent(msText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                        .addComponent(flowButton))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 162, Short.MAX_VALUE)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void programComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_programComboActionPerformed
        String program= (String) programCombo.getSelectedItem();
        if (defaultPrograms.get(program) != null)
            textPane.setText( defaultPrograms.get(program) );
    }//GEN-LAST:event_programComboActionPerformed

    protected void enableControls() {
        textPane.setEnabled(true);
        startStopButton.setText("START");
        relaxButton.setEnabled(true);
        interfaceCombo.setEnabled(true);
        programCombo.setEnabled(true);
    }
    
    private void startStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startStopButtonActionPerformed
        if (thread == null || thread.isRunning() == false) {
            // start
            if (hwInterface == null) {
                JOptionPane.showMessageDialog(this, "must select hardware interface");
                return;
            }

            try {
                
                float actualMs= Float.parseFloat( msText.getText() );
                thread= new ParserThread(textPane.getText(),actualMs);
                (new Thread(thread)).start();
                
                textPane.setEnabled(false);
                startStopButton.setText("STOP");
                relaxButton.setEnabled(false);
                interfaceCombo.setEnabled(false);
                programCombo.setEnabled(false);

            } catch (ParserException e) {
                JOptionPane.showMessageDialog(this, "could not parse : "+ e.getMessage());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "could not parse : "+ e.getMessage());
            }
                
        } else {
            // stop
            thread.stop(); // will call enableControls()
        }
        
    }//GEN-LAST:event_startStopButtonActionPerformed

    private void relaxButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_relaxButtonActionPerformed
        if (hwInterface == null) {
            JOptionPane.showMessageDialog(this, "must select hardware interface");
            return;
        }
        try {
            setXDegrees(0);
            setYDegrees(0);
        } catch (HardwareInterfaceException e) {
            log.info("could not relax : "+ e);
        }
    }//GEN-LAST:event_relaxButtonActionPerformed

    private void textPaneKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_textPaneKeyTyped
        for(String name : defaultPrograms.keySet())
            if (defaultPrograms.get(name) == null)
                programCombo.setSelectedItem(name);
    }//GEN-LAST:event_textPaneKeyTyped

    private void interfaceComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interfaceComboActionPerformed
        if (hwInterface != null)
            hwInterface.close();
        
        int n= interfaceCombo.getSelectedIndex();
        
        if (n <= 0)
            hwInterface= null;
        else try {
            
            hwInterface= (ServoInterface)
                    ServoInterfaceFactory.instance().getInterface(n-1);
            hwInterface.open();
            
            // we need to be in well defined position in the beginning
            relaxButtonActionPerformed(evt);
            
        } catch (HardwareInterfaceException e) {
            JOptionPane.showMessageDialog(this, "could not open interface : " + e.getMessage());
        }
    }//GEN-LAST:event_interfaceComboActionPerformed

    private void msTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_msTextActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_msTextActionPerformed

    /**
     * sets the speed of the servo in X direction 
     * 
     * @param dgdt in degrees/s
     */
    public void setSpeedX(float dgdt) throws HardwareInterfaceException {
        if (veloThread != null)
            veloThread.setSpeedX(dgdt); // Todo: SpeedConvert(dgdt)*veloThread.getDt()
    }
    
   
    
    
    /**
     * sets the speed of the servo in Y direction 
     * 
     * @param dgdt in degrees/s
     */
    public void setSpeedY(float dgdt) throws HardwareInterfaceException {
        if (veloThread != null)
            veloThread.setSpeedY(dgdt); //TODO SpeedConvert(dgdt)*veloThread.getDt()
    }
    
    protected VelocityThread veloThread= null;
    
    private void flowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flowButtonActionPerformed
        if (veloThread == null) {
            // hand over control to velocityThread
            veloThread= new VelocityThread();
            veloThread.start();
            interfaceCombo.setEnabled(false);
            programCombo.setEnabled(false);
            startStopButton.setEnabled(false);
            relaxButton.setEnabled(false);
            msText.setEnabled(false);
        } else {
            // hand over control to parser
            veloThread.cancel();
            try { 
                veloThread.join();
            } catch (InterruptedException e) {
                log.info("interrupted while waiting for velocity thread");
            }
            veloThread= null;
            
            interfaceCombo.setEnabled(true);
            programCombo.setEnabled(true);
            startStopButton.setEnabled(true);
            relaxButton.setEnabled(true);
            msText.setEnabled(true);
        }
    }//GEN-LAST:event_flowButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton flowButton;
    private javax.swing.JComboBox interfaceCombo;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel msLabel;
    private javax.swing.JTextField msText;
    private javax.swing.JComboBox programCombo;
    private javax.swing.JButton relaxButton;
    private javax.swing.JButton startStopButton;
    private javax.swing.JTextPane textPane;
    // End of variables declaration//GEN-END:variables




    protected class ParserException extends Exception 
    {
        public ParserException(String msg) {
            super(msg);
        }
    }
    
    protected abstract class ParserEnvelope
    {
        /**
         * @param t specifies how far this junk has proceeded already [0..1]
         * @return a fractional value of the integrated envelope [0..1]
         */
        public abstract float getValue(float t);
    }

    protected class RectangularEnvelope extends ParserEnvelope
    {
        @Override
        public float getValue(float t) {
            return t;
        }
    }

    protected class AccelerateDeccelerateLinearlyEnvelope extends ParserEnvelope
    {
        protected float fraction;

        public AccelerateDeccelerateLinearlyEnvelope(float fraction) {
            this.fraction= fraction;
        }

        @Override
        public float getValue(float t) {
            float x= fraction; // shorter
            float max= x*x + (1-2*x)*2*x + x*x;
            if (t<x)
                return t*t /max;
            else if (t<1-x)
                return (x*x + (t-x)*2*x) /max;
            else
                return (x*x + (1-2*x)*2*x + x*x-(1-t)*(1-t)) /max;
        }
    }

    protected class ParserJunk 
    {
        protected boolean polar; // whether to use polar coordinates
        protected float dx,dy; // increments in this junk (dx also means dr, dy also means dtheta)
        protected float t;
        protected ParserEnvelope envelope;

        ParserJunk(String s) throws ParserException, NumberFormatException
        {
            String[] tokens= s.split(",");

            if (tokens.length != 5)
                throw new ParserException("junk must consist of 5 values");

            if (tokens[0].equals("x"))
                polar= false;
            else if (tokens[0].equals("p"))
                polar= true;
            else
                throw new ParserException("coordinates '"+tokens[0]+"' not valid");

            if (tokens[1].equals("r"))
                envelope= new RectangularEnvelope();
            else if (tokens[1].equals("q2"))
                envelope= new AccelerateDeccelerateLinearlyEnvelope(0.2f);
            else if (tokens[1].equals("q3"))
                envelope= new AccelerateDeccelerateLinearlyEnvelope(0.3f);
            else
                throw new ParserException("envelope '"+tokens[1]+"' not valid");

            t = Float.parseFloat(tokens[2]);
            dx= Float.parseFloat(tokens[3]);
            dy= Float.parseFloat(tokens[4]);
        }

        public float getTime() { return t; }
        
        protected float getTheta(float x0,float y0) {
                if (x0>0)
                    return (float)( 180/Math.PI*Math.atan(y0/x0) );
                else if (x0<0) 
                    return  (float)( 180- 180/Math.PI*Math.atan(y0/x0) );
                return 0; // x==0
        }
        protected float getR(float x0,float y0) {
            return (float) Math.sqrt(x0*x0 + y0*y0);
        }

        public float addX(float x0,float y0,float t) {
            if (polar) {
                float r    = dx* envelope.getValue(t/this.t);
                float theta= dy* envelope.getValue(t/this.t);
                return (getR(x0,y0)+r)*((float) Math.cos((getTheta(x0,y0)+theta)/180*Math.PI));
            } else
                return x0 + dx* envelope.getValue(t/this.t);
        }

        public float addY(float x0,float y0,float t) { 
            if (polar) {
                float r    = dx* envelope.getValue(t/this.t);
                float theta= dy* envelope.getValue(t/this.t);
                return (getR(x0,y0)+r)*((float) Math.sin((getTheta(x0,y0)+theta)/180*Math.PI));
            } else
                return y0 + dy* envelope.getValue(t/this.t);
        }
    }
    
    
    public class VelocityThread extends Thread
    {
        
        protected float dt;
        protected boolean stop;
        protected float dx,dy;
        protected float minX,minY,maxX,maxY;

        public VelocityThread() {
            super();
            
            dt= 0.01f; // in seconds
            
            stop= false;
            dx= dy= 0;
            minX=minY=0.1f;
            maxX=maxY=0.9f;
        }

        @Override
        public void run() {
            int i= 0;
            long last= System.currentTimeMillis();
            float actualMs= Float.parseFloat( msText.getText() );

            while(!stop) {
                try {
                    sleep((long) (1000 *actualMs *dt));
                    i++;
                    if (System.currentTimeMillis()-last >=1000) {
                        log.info("[velocityThread] firing at "+i+" Hertz"); //DBG
                        i=0;
                        last= System.currentTimeMillis();
                    }
                    
                    float fx= (float) Math.max(minX,Math.min(maxX, convert(getXDegrees()) +dx ));
                    float fy= (float) Math.max(minY,Math.min(maxY, convert(getYDegrees()) +dy ));
                    setXDegrees( unconvert(fx) );
                    setYDegrees( unconvert(fy) );
                    
                } catch (InterruptedException e) {
                    log.info("[VelocityThread] interrupted " +e);
                    stop= true;
                } catch (HardwareInterfaceException e) {
                    log.info("[VelocityThread] hardware exception " +e);
                }
            }
        }

        public boolean isRunning() {
            return !stop;
        }

        public void cancel() {
            this.stop = true;
        }
        
        public float getDt() {
            return dt;
        }
        
        /**
         * sets the speed of the servo in X direction 
         * 
         * @param fdt in float-value/dt
         */
        public void setSpeedX(float fdt) {
            dx= fdt;
        }
        
        /**
         * sets the speed of the servo in Y direction 
         * 
         * @param fdt in float-value/dt
         */
        public void setSpeedY(float fdt) {
            dy= fdt;
        }
        
    }
    
    //protected static float java_dt= 0f; //hack
    
    protected class ParserThread implements Runnable
    {
        
        protected ArrayList<ParserJunk> junks;
        protected boolean running,stop;
        protected float dt; // timestep between two commands
        protected float actualMs;

        public ParserThread(String program, float actualMs) 
                throws ParserException, NumberFormatException
        {
            junks= new ArrayList<ParserJunk>();
            
            for(String junk : program.split("[ \t\n]+"))
                junks.add(new ParserJunk(junk));
            
            dt= .01f; // run at hundred hertz
            this.actualMs= actualMs;
            running= false;
        }
        
        public String debug() {
            return "nothing yet";
        }
        

        @Override
        public void run() {
            stop= false;
            running= true;
            
            long start= System.currentTimeMillis();
            
//            if (java_dt==0)
//                java_dt= dt; // initialize
            
            float t=0f,lt=0f; // current time, time after last junk
            int i=0;
            float x=getXDegrees(),y=getYDegrees();
            try {
            
                for (ParserJunk j : junks)
                {
                    while( t-lt < j.getTime() )
                    {
                        
                        setXDegrees( j.addX(x,y,t-lt) );
                        setYDegrees( j.addY(x,y,t-lt) );
                        
                        Thread.sleep((long)(1000*dt *actualMs));
                        t+=dt;
                        if (stop) break;
                    }
                    if (stop) break;
                    
                    lt+= j.getTime();
                    float y0=y, x0=x;
                    x  = j.addX(x0,y0,j.getTime());
                    y  = j.addY(x0,y0,j.getTime());
                    i += 1;
                    
                    log.info("after junk #"+i+": t="+t+" x="+x+" y="+y);
                }
            
            } catch (InterruptedException e) {
                log.info("[ParserThread] interrupted : " + e);
            } catch (HardwareInterfaceException e) {
                log.warning("[ParserThread] hardware exception : " + e);
            }
            
            long realMillis= System.currentTimeMillis() - start;
            log.info("run "+realMillis+"ms instead of "+((long) (1000*t))+"ms");
//            java_dt *= 1000*t/realMillis; // correct
            
            running= false;
            
            // hack
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    enableControls();
                }
            });
        }
        
        public boolean isRunning() {
            return running;
        }
        
        public void stop() {
            stop= true;
        }
    }
}
