/*PanTiltGUI.java
 *
 * Created on April 21, 2008, 11:50 AM */

package ch.unizh.ini.jaer.hardware.pantilt;

import ch.unizh.ini.jaer.hardware.pantilt.PanTiltAimer.Message;
import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.ExceptionListener;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.BasicStroke;
import java.awt.event.*;
import java.awt.geom.*;
import java.beans.*;
import java.util.logging.Logger;

/**Tests pan-tilt by mimicking mouse movements. Also can serve as calibration
 * source (for fixed DVS+Laser-pointer) via PropertyChangeSupport.
 * 
 * The GUI allows to record a path with the mouse that can then be looped by
 * the pan-tilt. The current target for the saccadic targeting can be displayed
 * as well as the jittered target. The path of the pan-tilt can be displayed 
 * for the resent past as well.
 * A dashed box is shown that indicates the LimitOfPan and LimitOfTilt as set 
 * by the hardware.
 * @author tobi */
public class PanTiltAimerGUI extends javax.swing.JFrame implements PropertyChangeListener, ExceptionListener {

    static  final Logger log = Logger.getLogger("PanTiltGUI");
    static  final float dash1[] = {10.0f};
    static  final BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10.0f, dash1, 0.0f);
    private final PropertyChangeSupport supportPTAimerGUI = new PropertyChangeSupport(this);
    private final PanTilt panTilt;
    private final Trajectory trajectory = new Trajectory();
    private final Trajectory panTiltTrajectory = new Trajectory();
    private final Trajectory targetTrajectory = new Trajectory();
    private final Trajectory jitterTargetTrajectory = new Trajectory();
    private int w = 200, h = 200;
    private float speed = .02f;
    private boolean recordingEnabled = false;   

    class Trajectory extends ArrayList<TrajectoryPoint> {
        long lastTime;
        TrajectoryPlayer player = null;
        
        void start() {
            lastTime = System.currentTimeMillis();
        }
        
        void add(float pan, float tilt, int x, int y) {
            if (isEmpty()) start();

            long now = System.currentTimeMillis();
            add(new TrajectoryPoint(now - lastTime, pan, tilt, x, y));
            lastTime = now;
        }

        @Override
        public void clear() {
            if (player != null) player.cancel();
            super.clear();
        }

        private void setPlaybackEnabled(boolean selected) {
            if (player != null) player.cancel();
            if (selected) {
                player = new TrajectoryPlayer();
                player.start();
            }
        }

        private void paintPath(Color LineColor,int NumberPoints) {
            if (isEmpty()) return;
            
            int n = size();
            int[] x = new int[n], y = new int[n];
            //If there are more than X points, only draw the newest X ones so that the screen is not all cluttered.
            for (int i = 0; i < Math.min(n,NumberPoints); i++) { 
                x[i] = get(i+Math.max(0,n-NumberPoints)).x;
                y[i] = get(i+Math.max(0,n-NumberPoints)).y;
            }
            Graphics g = calibrationPanel.getGraphics();
            g.setColor(LineColor);
            g.drawPolyline(x, y, Math.min(n,NumberPoints));
        }
        
        private void paintCrossHair(Color LineColor) {
            if (isEmpty()) return;
            
            int n = size();
            int x = get(n-1).x, y = get(n-1).y;
            Graphics g = calibrationPanel.getGraphics();
            g.setColor(LineColor);
            g.drawLine(x-5,y,x+5,y);
            g.drawLine(x,y-5,x,y+5);
            
            g.drawLine(x-10,y+5,x-10,y+10);g.drawLine(x-10,y-5,x-10,y-10);
            g.drawLine(x+10,y+5,x+10,y+10);g.drawLine(x+10,y-5,x+10,y-10);
            
            g.drawLine(x+5,y-10,x+10,y-10);g.drawLine(x-5,y-10,x-10,y-10);
            g.drawLine(x+5,y+10,x+10,y+10);g.drawLine(x-5,y+10,x-10,y+10);
        }

        class TrajectoryPlayer extends Thread {
            boolean cancelMe = false;

            void cancel() {
                cancelMe = true;
                synchronized (this) {
                    interrupt();
                }
            }

            @Override
            public void run() {
                while (!cancelMe) {
                    for (TrajectoryPoint p : Trajectory.this) {
                        if (cancelMe) break;

                        panTilt.setTarget(p.pan, p.tilt);
                        try {
                            Thread.sleep(p.timeMillis);
                        } catch (InterruptedException ex) {
                            break;
                        }
                    }
                }
            }
        }
    }

    class TrajectoryPoint {

        long timeMillis;
        float pan, tilt;
        int x, y;

        public TrajectoryPoint(long timeMillis, float pan, float tilt, int x, int y) {
            this.timeMillis = timeMillis;
            this.pan = pan;
            this.tilt = tilt;
            this.x = x;
            this.y = y;
        }
    }
    
    /** Make the GUI.
     * @param pt the pan tilt unit */
    public PanTiltAimerGUI(PanTilt pt) {
        panTilt = pt;
        panTilt.addPropertyChangeListener(this); //We want to know the current position of the panTilt as it changes
        initComponents();
        calibrationPanel.setPreferredSize(new Dimension(w, h));
        calibrationPanel.requestFocusInWindow();
        
        setSpeedTB.setText(String.format("%.2f", speed));
        pack();
    }
    
    @Override public void propertyChange(PropertyChangeEvent evt) {
        float[] NewV = (float[])evt.getNewValue();
        Point MouseN = getMouseFromPanTilt(new Point2D.Float(NewV[0],NewV[1]));
        switch (evt.getPropertyName()) {
            case "PanTiltValues":
                this.SetPanTB.setText(String.format("%.2f", NewV[0]));
                this.SetTiltTB.setText(String.format("%.2f", NewV[1]));
                this.panTiltTrajectory.add(NewV[0], NewV[1], (int)MouseN.getX(), (int)MouseN.getY());
                if(showPosCB.isSelected()) repaint();
                break;
            case "Target":
                this.targetTrajectory.add(NewV[0], NewV[1],(int)MouseN.getX(), (int)MouseN.getY());
                if(showTargetCB.isSelected()) repaint();
                break;
            case "JitterTarget":
                this.jitterTargetTrajectory.add(NewV[0], NewV[1],(int)MouseN.getX(), (int)MouseN.getY());
                if(showTargetCB.isSelected()) repaint();
                break;
        }
    }

    @Override public void paint(Graphics g) {
        super.paint(g);
        
        paintLimitBox(calibrationPanel); //paints a dashed box indicating the user defined pan and tilt limits
        trajectory.paintPath(Color.black,1000);
        if(showPosCB.isSelected()) panTiltTrajectory.paintPath(Color.red,1000);
        if(showTargetCB.isSelected()) targetTrajectory.paintCrossHair(Color.blue);
        if(showTargetCB.isSelected()) jitterTargetTrajectory.paintCrossHair(Color.green);
    }

    /**This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor. */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        statusLabel = new javax.swing.JLabel();
        calibrationPanel = new javax.swing.JPanel();
        InfoLabel = new javax.swing.JLabel();
        recordCB = new javax.swing.JCheckBox();
        clearBut = new javax.swing.JButton();
        loopBut = new javax.swing.JToggleButton();
        centerBut = new javax.swing.JButton();
        relaxBut = new javax.swing.JButton();
        SetPanTB = new javax.swing.JTextField();
        SetTiltTB = new javax.swing.JTextField();
        TiltLabel = new javax.swing.JLabel();
        PanLabel = new javax.swing.JLabel();
        showPosCB = new javax.swing.JCheckBox();
        showTargetCB = new javax.swing.JCheckBox();
        jitterBut = new javax.swing.JButton();
        setSpeedTB = new javax.swing.JTextField();
        SpeedLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("PanTiltAimer");
        setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        setMinimumSize(new java.awt.Dimension(650, 700));
        setPreferredSize(new java.awt.Dimension(700, 750));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        statusLabel.setText("exception status");
        statusLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        calibrationPanel.setBackground(new java.awt.Color(255, 255, 255));
        calibrationPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        calibrationPanel.setToolTipText("Drag or click mouse to aim pan-tilt");
        calibrationPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                calibrationPanelMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                calibrationPanelMouseExited(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                calibrationPanelMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                calibrationPanelMouseReleased(evt);
            }
        });
        calibrationPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                calibrationPanelComponentResized(evt);
            }
        });
        calibrationPanel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                calibrationPanelMouseDragged(evt);
            }
        });
        calibrationPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                calibrationPanelKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout calibrationPanelLayout = new javax.swing.GroupLayout(calibrationPanel);
        calibrationPanel.setLayout(calibrationPanelLayout);
        calibrationPanelLayout.setHorizontalGroup(
            calibrationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        calibrationPanelLayout.setVerticalGroup(
            calibrationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 463, Short.MAX_VALUE)
        );

        InfoLabel.setFont(InfoLabel.getFont().deriveFont((InfoLabel.getFont().getStyle() & ~java.awt.Font.ITALIC) & ~java.awt.Font.BOLD));
        InfoLabel.setText("<html>Drag or click the <b>mouse</b> or use the <b>arrow keys</b> to aim pan tilt. <br>Use <b>r</b> to toggle recording a trajectory and <b>esc</b> to delete recorded trjectory. <br>Dashed lines show limits of pantilt.</html>");

        recordCB.setText("Record path");
        recordCB.setToolTipText("Draw a fixed path with the mouse");
        recordCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordCBActionPerformed(evt);
            }
        });

        clearBut.setText("Clear path");
        clearBut.setToolTipText("clears a previously set path");
        clearBut.setMargin(new java.awt.Insets(2, 5, 2, 5));
        clearBut.setMaximumSize(new java.awt.Dimension(41, 23));
        clearBut.setMinimumSize(new java.awt.Dimension(41, 23));
        clearBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButActionPerformed(evt);
            }
        });

        loopBut.setText("Loop path");
        loopBut.setToolTipText("loops a previously set path");
        loopBut.setMaximumSize(new java.awt.Dimension(41, 23));
        loopBut.setMinimumSize(new java.awt.Dimension(41, 23));
        loopBut.setOpaque(true);
        loopBut.setPreferredSize(new java.awt.Dimension(83, 23));
        loopBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loopButActionPerformed(evt);
            }
        });

        centerBut.setText("Center");
        centerBut.setToolTipText("Centers the pan-tilt to 0.5/0.5");
        centerBut.setMargin(new java.awt.Insets(2, 5, 2, 5));
        centerBut.setMaximumSize(new java.awt.Dimension(41, 23));
        centerBut.setMinimumSize(new java.awt.Dimension(41, 23));
        centerBut.setPreferredSize(new java.awt.Dimension(83, 23));
        centerBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centerButActionPerformed(evt);
            }
        });

        relaxBut.setText("Relax");
        relaxBut.setToolTipText("Disables the servos");
        relaxBut.setMargin(new java.awt.Insets(2, 5, 2, 5));
        relaxBut.setPreferredSize(new java.awt.Dimension(83, 23));
        relaxBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                relaxButActionPerformed(evt);
            }
        });

        SetPanTB.setText("init...");
        SetPanTB.setToolTipText("Enter pan value to set pan. Current value is displayed");
        SetPanTB.setPreferredSize(new java.awt.Dimension(40, 20));
        SetPanTB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SetPanTBActionPerformed(evt);
            }
        });

        SetTiltTB.setText("init...");
        SetTiltTB.setToolTipText("Enter tilt value to set tilt. Current value is displayed");
        SetTiltTB.setPreferredSize(new java.awt.Dimension(40, 20));
        SetTiltTB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SetTiltTBActionPerformed(evt);
            }
        });

        TiltLabel.setText("Tilt:");

        PanLabel.setText("Pan:");

        showPosCB.setText("show position");
        showPosCB.setToolTipText("");
        showPosCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showPosCBActionPerformed(evt);
            }
        });

        showTargetCB.setText("show target");
        showTargetCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showTargetCBActionPerformed(evt);
            }
        });

        jitterBut.setText("Toggle jitter");
        jitterBut.setToolTipText("toggles the jitter of servos.");
        jitterBut.setMargin(new java.awt.Insets(2, 5, 2, 5));
        jitterBut.setMaximumSize(new java.awt.Dimension(41, 23));
        jitterBut.setMinimumSize(new java.awt.Dimension(41, 23));
        jitterBut.setPreferredSize(new java.awt.Dimension(83, 23));
        jitterBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jitterButActionPerformed(evt);
            }
        });

        setSpeedTB.setText("init...");
        setSpeedTB.setToolTipText("Enter the speed controlled with the arrow keys");
        setSpeedTB.setPreferredSize(new java.awt.Dimension(40, 20));
        setSpeedTB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setSpeedTBActionPerformed(evt);
            }
        });

        SpeedLabel.setText("Speed:");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(statusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(calibrationPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(InfoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 184, Short.MAX_VALUE)
                        .addGap(18, 18, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(showPosCB)
                            .addComponent(showTargetCB)
                            .addComponent(recordCB))
                        .addGap(9, 9, 9)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(loopBut, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(clearBut, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(relaxBut, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(centerBut, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jitterBut, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(24, 24, 24)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(SpeedLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(TiltLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(setSpeedTB, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(SetTiltTB, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(PanLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(SetPanTB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(loopBut, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(centerBut, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(SetTiltTB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(TiltLabel))
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(clearBut, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(relaxBut, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(showTargetCB))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(recordCB)
                                    .addComponent(jitterBut, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(setSpeedTB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(SpeedLabel)))))
                    .addComponent(InfoLabel)
                    .addComponent(showPosCB)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(SetPanTB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(PanLabel))))
                .addGap(10, 10, 10)
                .addComponent(calibrationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(5, 5, 5)
                .addComponent(statusLabel)
                .addGap(10, 10, 10))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private float getPan(MouseEvent evt) {
        int x = evt.getX();
        float pan = (float) x / w;
        return pan;
    }

    private float getTilt(MouseEvent evt) {
        int y = evt.getY();
        float tilt = 1 - (float) (h - y) / h;
        return tilt;
    }

    public Point getMouseFromPanTilt(Point2D.Float pt) {
        return new Point((int) (calibrationPanel.getWidth() * pt.x), (int) (calibrationPanel.getHeight() * pt.y));
    }

    public Point2D.Float getPanTiltFromMouse(Point mouse) {
        return new Point2D.Float((float) mouse.x / calibrationPanel.getWidth(), (float) mouse.y / calibrationPanel.getHeight());
    }
    

    @Override public void exceptionOccurred(Exception x, Object source) {
        statusLabel.setText(x.getMessage());
    }

    /** Property change events are fired to return events
     * For sample messages "sample", the Point2D.Float object that is returned
     * is the pan,tilt value for that point, i.e., the last pan,tilt value that
     * has been set.
     *
     * When samples have been chosen, "done" is passed.
     *
     * @return the support. Add yourself as a listener to get notifications of
     * new user aiming points. */
    public PropertyChangeSupport getSupport() {
        return supportPTAimerGUI;
    }

    /**
     * @return the recordingEnabled */
    private boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    /**
     * @param recordingEnabled the recordingEnabled to set */
    private void setRecordingEnabled(boolean recordingEnabled) {
        boolean old = this.recordingEnabled;
        this.recordingEnabled = recordingEnabled;
        recordCB.setSelected(recordingEnabled);
        supportPTAimerGUI.firePropertyChange(Message.SetRecordingEnabled.name(), old, recordingEnabled);
    }  
    
    private void paintLimitBox(javax.swing.JPanel printPanel) {
        w = printPanel.getWidth();
        h = printPanel.getHeight();
        Graphics2D calibrationGraph = (Graphics2D) printPanel.getGraphics();//Need Graphics2D for dashed stroke
        int P1 = (int)(w/2-w*panTilt.getLimitOfPan());
        int P2 = (int)(w/2+w*panTilt.getLimitOfPan());
        int T1 = (int)(h/2-h*panTilt.getLimitOfTilt());
        int T2 = (int)(h/2+h*panTilt.getLimitOfTilt());
        
        calibrationGraph.setStroke(dashed);
        calibrationGraph.drawLine(P1,T1, P2, T1);//Tilt Limiting Lines
        calibrationGraph.drawLine(P1,T2, P2, T2);
        
        calibrationGraph.drawLine(P1,T1,P1,T2);//Pan Limiting Lines
        calibrationGraph.drawLine(P2,T1,P2,T2);    
    }

    private void calibrationPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_calibrationPanelComponentResized
        w = calibrationPanel.getWidth();
        h = calibrationPanel.getHeight();
    }//GEN-LAST:event_calibrationPanelComponentResized

    private void calibrationPanelMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrationPanelMouseDragged
        float pan  = getPan(evt);
        float tilt = getTilt(evt);
        panTilt.setTarget(pan, tilt);
        if (isRecordingEnabled()) {
            trajectory.add(pan, tilt, evt.getX(), evt.getY());
            repaint();
        }
    }//GEN-LAST:event_calibrationPanelMouseDragged

    private void calibrationPanelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrationPanelMousePressed
        float pan  = getPan(evt);
        float tilt = getTilt(evt);
        panTilt.setTarget(pan, tilt);
    }//GEN-LAST:event_calibrationPanelMousePressed

    private void calibrationPanelKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_calibrationPanelKeyPressed
        float[] curTarget = panTilt.getTarget();
        float newVal;
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_R:
                // send a message with pantilt and mouse filled in, tracker will fill in retina if there is a tracked locaton
                setRecordingEnabled(!isRecordingEnabled());
                repaint();
                break;
            case KeyEvent.VK_ESCAPE:
                supportPTAimerGUI.firePropertyChange(Message.AbortRecording.name(), null, null);
                trajectory.clear();
                setRecordingEnabled(false);
                break;
            case KeyEvent.VK_DOWN:
                // These checks are neccesary as otherwise the target can be 
                // WAY out of bounds and it takes many key presses to get the 
                // target back into a reasonable range.
                if(curTarget[1]+speed > 1) newVal = 1;
                else newVal = curTarget[1]+speed;
                panTilt.setTarget(curTarget[0], newVal);
                break;
            case KeyEvent.VK_UP:
                if(curTarget[1]-speed < 0) newVal = 0;
                else newVal = curTarget[1]-speed;
                panTilt.setTarget(curTarget[0], newVal);
                break;
            case KeyEvent.VK_RIGHT:
                if(curTarget[0]+speed > 1) newVal = 1;
                else newVal = curTarget[0]+speed;
                panTilt.setTarget(newVal, curTarget[1]);
                break;
            case KeyEvent.VK_LEFT:
                if(curTarget[0]-speed < 0) newVal = 0;
                else newVal = curTarget[0]-speed;
                panTilt.setTarget(newVal, curTarget[1]);
                break;
            default:
                Toolkit.getDefaultToolkit().beep();
        }
    }//GEN-LAST:event_calibrationPanelKeyPressed

    private void calibrationPanelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrationPanelMouseReleased
        float pan  = getPan(evt);
        float tilt = getTilt(evt);
        panTilt.setTarget(pan, tilt);
    }//GEN-LAST:event_calibrationPanelMouseReleased

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        panTilt.setJitterEnabled(false);
    }//GEN-LAST:event_formWindowClosed

    private void calibrationPanelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrationPanelMouseEntered
        setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        calibrationPanel.requestFocus();
        repaint();//So that the limiterBox is updated when the user adjustet values
    }//GEN-LAST:event_calibrationPanelMouseEntered

    private void calibrationPanelMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_calibrationPanelMouseExited
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_calibrationPanelMouseExited

    private void clearButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButActionPerformed
        supportPTAimerGUI.firePropertyChange(Message.ClearRecording.name(), null, null);
        trajectory.clear();
        repaint();
    }//GEN-LAST:event_clearButActionPerformed

    private void recordCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordCBActionPerformed
        setRecordingEnabled(recordCB.isSelected());
    }//GEN-LAST:event_recordCBActionPerformed

    private void loopButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loopButActionPerformed
        trajectory.setPlaybackEnabled(loopBut.isSelected());
    }//GEN-LAST:event_loopButActionPerformed

    private void centerButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centerButActionPerformed
        panTilt.setTarget(.5f, .5f);
    }//GEN-LAST:event_centerButActionPerformed

    private void relaxButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_relaxButActionPerformed
        if (panTilt != null && panTilt.getServoInterface() != null) {
            try {
                panTilt.setJitterEnabled(false);
                panTilt.stopFollow();
                panTilt.getServoInterface().disableAllServos();
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }//GEN-LAST:event_relaxButActionPerformed

    private void SetTiltTBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SetTiltTBActionPerformed
        float[] Old = panTilt.getPanTiltValues();
        panTilt.setTarget(Old[0],Float.parseFloat(SetTiltTB.getText()));
    }//GEN-LAST:event_SetTiltTBActionPerformed

    private void SetPanTBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SetPanTBActionPerformed
        float[] Old = panTilt.getPanTiltValues();
        panTilt.setTarget(Float.parseFloat(SetPanTB.getText()), Old[1]);
    }//GEN-LAST:event_SetPanTBActionPerformed

    private void showPosCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPosCBActionPerformed
        repaint();
    }//GEN-LAST:event_showPosCBActionPerformed

    private void showTargetCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showTargetCBActionPerformed
        repaint();
    }//GEN-LAST:event_showTargetCBActionPerformed

    private void jitterButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jitterButActionPerformed
        panTilt.setJitterEnabled(!panTilt.isJitterEnabled());
    }//GEN-LAST:event_jitterButActionPerformed

    private void setSpeedTBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setSpeedTBActionPerformed
        speed = Float.parseFloat(setSpeedTB.getText());
        if(speed < 0) {
            speed = 0;
            setSpeedTB.setText(String.format("%.2f", 0f));
        }
    }//GEN-LAST:event_setSpeedTBActionPerformed
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel InfoLabel;
    private javax.swing.JLabel PanLabel;
    private javax.swing.JTextField SetPanTB;
    private javax.swing.JTextField SetTiltTB;
    private javax.swing.JLabel SpeedLabel;
    private javax.swing.JLabel TiltLabel;
    private javax.swing.JPanel calibrationPanel;
    private javax.swing.JButton centerBut;
    private javax.swing.JButton clearBut;
    private javax.swing.JButton jitterBut;
    private javax.swing.JToggleButton loopBut;
    private javax.swing.JCheckBox recordCB;
    private javax.swing.JButton relaxBut;
    private javax.swing.JTextField setSpeedTB;
    private javax.swing.JCheckBox showPosCB;
    private javax.swing.JCheckBox showTargetCB;
    private javax.swing.JLabel statusLabel;
    // End of variables declaration//GEN-END:variables
}
