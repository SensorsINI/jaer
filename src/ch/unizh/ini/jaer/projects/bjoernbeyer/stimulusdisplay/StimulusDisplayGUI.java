/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.util.ArrayList;
import javax.swing.DefaultListModel;

/**
 *
 * @author Bjoern
 */
public class StimulusDisplayGUI extends javax.swing.JFrame {

    private static final String[] STIM_LIST = {"oval","rectangle","vertical Stripes", "horizontal Stripes", "random dot Patch"};
    private static final String[] PAINT_LIST = {"no fill","black","sine grating(vert)","sine grating(horz)","stripes(vert)","stripes(horz)"};
    private static final String FILE_NAME_PREFIX = "jAER1.5_StimSet_";
    
    private ScreenActionCanvas ActionGUI;
    private StimulusFrame StimFrame;
    private LinearPathDesignerGUI pathDesigner;

    private DefaultListModel listModelStim, listModelSet;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    /**
     * Creates new form StimulusGUI
     * @param actionGui
     */
    public StimulusDisplayGUI(ScreenActionCanvas actionGui){
        initComponents();
        StimFrame = new StimulusFrame();

        pathDesigner = new LinearPathDesignerGUI(StimFrame);
        
        //This GUI is split into two parts. The actual ActionPanel and the control panel.
        // This is because the ActionPanel is supposed to be displayed on a seperate display that might 
        // be out of sight of the primary display. Hence the control can be on the main
        // display while the calibration/stimulus can still be displayed on the secondary.
        ActionGUI = actionGui;
        ActionGUI.setContentPane(StimFrame);
        
        
        SelectStimCBOX.setSelectedIndex(0);
        SelectPaintCBOX.setSelectedIndex(0);
        
        listModelStim = new DefaultListModel();
        listModelSet  = new DefaultListModel();
        avlbStimLIST.setModel(listModelStim);
        avlbSetLIST.setModel(listModelSet);
        
        //When the Filter is starting up we look for files that have the naming format of the stimulus sets.
        // We preload those into an array such that we can access them later.
        File f = new File(System.getProperty("user.home"));
        File[] matchingFiles = f.listFiles(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) {
                return name.startsWith(FILE_NAME_PREFIX) && name.endsWith("txt");
            }
        });
        for(File g : matchingFiles){
            int lastDot  = g.getName().lastIndexOf(".");
            int lastDash = g.getName().lastIndexOf("_");
            listModelSet.addElement(g.getName().substring(lastDash+1, lastDot));
        }
        if(avlbSetLIST.getModel().getSize()>0) {
            avlbSetLIST.setSelectedIndex(0);
        }
    }
    
    public StimulusDisplayGUI() {
        this(new ScreenActionCanvas());
    }
    
    @Override public void dispose() {
        ActionGUI.dispose(); // The two windows should behave as if they where one
        if(pathDesigner.isVisible()){
            pathDesigner.dispose();
        }
        super.dispose();
    }
    
    
    @Override public void setVisible(boolean visible) {
        //If the Action GUI is not displayable it means 
        // it is either disposed or not realized
        if(!ActionGUI.isShowing()) { 
            ActionGUI.setVisible(true);
            ActionGUI.requestFocus();
        }
        super.setVisible(visible);
    }
    
    public PaintableObject getListedStimulus(Object listValue) {
        return StimFrame.getObject(getListedStimulusIndex(listValue));
    }
    
    public int getListedStimulusIndex(Object listValue) {
        if(listValue == null) throw new IllegalArgumentException("The passed listValue is null!");
        String[] parts = ((String) listValue).split(":");
        
        return Integer.parseInt(parts[0]);
    }
            
    public PaintableObject getCurrentListedStimulus() {
        return getListedStimulus(avlbStimLIST.getSelectedValue());
    }
    
    public int getCurrentListedStimulusIndex() {
        return getListedStimulusIndex(avlbStimLIST.getSelectedValue());
    }
    
    public void addObjectToFrameAndList(PaintableObject objToAdd) {
        int currentIndex = StimFrame.addObject(objToAdd);
        rebuiltStimList();
        avlbStimLIST.setSelectedIndex(currentIndex);
        this.pcs.firePropertyChange("objectAdded", null, objToAdd);
    }
    
    @Override public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }
    
    public Object[] getObjectListArray() {
        return StimFrame.getObjectList().toArray();
    }
    
    private void rebuiltStimList() {
        listModelStim.clear();
        for(int i=0;i<StimFrame.getObjectListSize();i++) {
            listModelStim.addElement(StimFrame.getObjectNameAtIndex(i));
        }
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        jPanel6 = new javax.swing.JPanel();
        SelectStimCBOX = new javax.swing.JComboBox(STIM_LIST);
        AddStimBUT = new javax.swing.JButton();
        recordPathTOGBUT = new javax.swing.JToggleButton();
        NameTXT = new javax.swing.JTextField();
        nameLBL = new javax.swing.JLabel();
        widthLBL = new javax.swing.JLabel();
        widthTXT = new javax.swing.JTextField();
        prop1TXT = new javax.swing.JTextField();
        prop1LBL = new javax.swing.JLabel();
        prop2TXT = new javax.swing.JTextField();
        prop2LBL = new javax.swing.JLabel();
        heightTXT = new javax.swing.JTextField();
        heightLBL = new javax.swing.JLabel();
        angleTXT = new javax.swing.JTextField();
        angleLBL = new javax.swing.JLabel();
        prop3TXT = new javax.swing.JTextField();
        prop3LBL = new javax.swing.JLabel();
        origYTXT = new javax.swing.JTextField();
        origYLBL = new javax.swing.JLabel();
        origXTXT = new javax.swing.JTextField();
        origXLBL = new javax.swing.JLabel();
        SelectPaintCBOX = new javax.swing.JComboBox(PAINT_LIST);
        FlashFreqTXT = new javax.swing.JTextField();
        FlashFreqLBL = new javax.swing.JLabel();
        strokeTXT = new javax.swing.JTextField();
        strokeLBL = new javax.swing.JLabel();
        linPathBUT = new javax.swing.JButton();
        prop4TXT = new javax.swing.JTextField();
        prop4LBL = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        avlbStimLIST = new javax.swing.JList();
        delStimBUT = new javax.swing.JButton();
        playPathBUT = new javax.swing.JButton();
        loopPathTOGBUT = new javax.swing.JToggleButton();
        showPathTOGBUT = new javax.swing.JToggleButton();
        flashStimTOGBUT = new javax.swing.JToggleButton();
        loopAllBUT = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        EditOrigXTXT = new javax.swing.JTextField();
        origYLBL1 = new javax.swing.JLabel();
        strokeLBL1 = new javax.swing.JLabel();
        EditHeightTXT = new javax.swing.JTextField();
        EditOrigYTXT = new javax.swing.JTextField();
        EditAngleTXT = new javax.swing.JTextField();
        angleLBL1 = new javax.swing.JLabel();
        widthLBL1 = new javax.swing.JLabel();
        origXLBL1 = new javax.swing.JLabel();
        EditFlashFreqTXT = new javax.swing.JTextField();
        EditWidthTXT = new javax.swing.JTextField();
        heightLBL1 = new javax.swing.JLabel();
        EditStrokeTXT = new javax.swing.JTextField();
        FlashFreqLBL1 = new javax.swing.JLabel();
        EditObjectPathBUT = new javax.swing.JButton();
        copyPathBUT = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        avlbSetLIST = new javax.swing.JList();
        setNameLBL = new javax.swing.JLabel();
        setNameTXT = new javax.swing.JTextField();
        avlbSetLBL = new javax.swing.JLabel();
        loadSetBUT = new javax.swing.JButton();
        deleteStimBUT = new javax.swing.JButton();
        saveSetBUT = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        ExceptionTXTAREA = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setMaximumSize(new java.awt.Dimension(1000, 1000));
        setMinimumSize(new java.awt.Dimension(550, 650));
        setPreferredSize(new java.awt.Dimension(550, 650));

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Stimulus creation"));

        SelectStimCBOX.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SelectStimCBOXActionPerformed(evt);
            }
        });

        AddStimBUT.setText("Add Stimulus");
        AddStimBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddStimBUTActionPerformed(evt);
            }
        });

        recordPathTOGBUT.setText("record path");
        recordPathTOGBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordPathTOGBUTActionPerformed(evt);
            }
        });

        nameLBL.setText("Name:");

        widthLBL.setText("width:");

        widthTXT.setText(".1");
        widthTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        prop1TXT.setText("init...");
        prop1TXT.setPreferredSize(new java.awt.Dimension(50, 20));

        prop1LBL.setText("init...");
        prop1LBL.setToolTipText("");

        prop2TXT.setText("init...");
        prop2TXT.setPreferredSize(new java.awt.Dimension(50, 20));

        prop2LBL.setText("init...");

        heightTXT.setText(".1");
        heightTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        heightLBL.setText("height:");

        angleTXT.setText("0");
        angleTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        angleLBL.setText("angle:");

        prop3TXT.setText("init...");
        prop3TXT.setPreferredSize(new java.awt.Dimension(50, 20));

        prop3LBL.setText("init...");

        origYTXT.setText("0");
        origYTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        origYLBL.setText("origY:");

        origXTXT.setText("0");
        origXTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        origXLBL.setText("origX:");

        SelectPaintCBOX.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SelectPaintCBOXActionPerformed(evt);
            }
        });

        FlashFreqTXT.setText("20");
        FlashFreqTXT.setPreferredSize(new java.awt.Dimension(50, 20));
        FlashFreqTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FlashFreqTXTActionPerformed(evt);
            }
        });

        FlashFreqLBL.setText("FlashFreq:");

        strokeTXT.setText("4");
        strokeTXT.setPreferredSize(new java.awt.Dimension(50, 20));

        strokeLBL.setText("stroke:");

        linPathBUT.setText("lin. path gui");
        linPathBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                linPathBUTActionPerformed(evt);
            }
        });

        prop4TXT.setText("init...");
        prop4TXT.setPreferredSize(new java.awt.Dimension(50, 20));

        prop4LBL.setText("init...");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel6Layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(AddStimBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(SelectStimCBOX, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(linPathBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(recordPathTOGBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(SelectPaintCBOX, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nameLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(origXLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(widthLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(strokeLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(prop1LBL, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(prop2LBL, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addGap(4, 4, 4)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(origXTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(widthTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(strokeTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(prop1TXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(prop2TXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(origYLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(heightLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(FlashFreqLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(angleLBL, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(prop3LBL, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(prop4LBL, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(prop4TXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(prop3TXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(angleTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(FlashFreqTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(heightTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(origYTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(NameTXT))))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(AddStimBUT)
                            .addComponent(recordPathTOGBUT))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(linPathBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(SelectStimCBOX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(SelectPaintCBOX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(NameTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(nameLBL))
                        .addGap(4, 4, 4)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(origYLBL)
                            .addComponent(origYTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(origXLBL)
                            .addComponent(origXTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(heightLBL)
                                .addComponent(heightTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(widthLBL)
                                .addComponent(widthTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(FlashFreqLBL)
                            .addComponent(FlashFreqTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(strokeLBL)
                            .addComponent(strokeTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(angleLBL)
                                    .addComponent(angleTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(prop3LBL)
                                    .addComponent(prop3TXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(25, 25, 25))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(prop1LBL)
                                    .addComponent(prop1TXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(prop2LBL)
                                    .addComponent(prop2TXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(prop4LBL)
                        .addComponent(prop4TXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Current stimuli"));

        avlbStimLIST.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        avlbStimLIST.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                avlbStimLISTValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(avlbStimLIST);

        delStimBUT.setText("remove stim");
        delStimBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                delStimBUTActionPerformed(evt);
            }
        });

        playPathBUT.setText("play Path");
        playPathBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playPathBUTActionPerformed(evt);
            }
        });

        loopPathTOGBUT.setText("loop Path");
        loopPathTOGBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loopPathTOGBUTActionPerformed(evt);
            }
        });

        showPathTOGBUT.setText("show Path");
        showPathTOGBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showPathTOGBUTActionPerformed(evt);
            }
        });

        flashStimTOGBUT.setText("flash Stim");
        flashStimTOGBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flashStimTOGBUTActionPerformed(evt);
            }
        });

        loopAllBUT.setText("loop all");
        loopAllBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loopAllBUTActionPerformed(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Edit stimulus"));

        EditOrigXTXT.setText("0");
        EditOrigXTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditOrigXTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditOrigXTXTActionPerformed(evt);
            }
        });

        origYLBL1.setText("origY:");

        strokeLBL1.setText("stroke:");

        EditHeightTXT.setText(".1");
        EditHeightTXT.setMinimumSize(new java.awt.Dimension(6, 15));
        EditHeightTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditHeightTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditHeightTXTActionPerformed(evt);
            }
        });

        EditOrigYTXT.setText("0");
        EditOrigYTXT.setMinimumSize(new java.awt.Dimension(6, 15));
        EditOrigYTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditOrigYTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditOrigYTXTActionPerformed(evt);
            }
        });

        EditAngleTXT.setText("0");
        EditAngleTXT.setMinimumSize(new java.awt.Dimension(6, 15));
        EditAngleTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditAngleTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditAngleTXTActionPerformed(evt);
            }
        });

        angleLBL1.setText("angle:");

        widthLBL1.setText("width:");

        origXLBL1.setText("origX:");

        EditFlashFreqTXT.setText("20");
        EditFlashFreqTXT.setMaximumSize(new java.awt.Dimension(30, 20));
        EditFlashFreqTXT.setMinimumSize(new java.awt.Dimension(30, 20));
        EditFlashFreqTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditFlashFreqTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditFlashFreqTXTActionPerformed(evt);
            }
        });

        EditWidthTXT.setText(".1");
        EditWidthTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditWidthTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditWidthTXTActionPerformed(evt);
            }
        });

        heightLBL1.setText("height:");

        EditStrokeTXT.setText("4");
        EditStrokeTXT.setPreferredSize(new java.awt.Dimension(30, 20));
        EditStrokeTXT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditStrokeTXTActionPerformed(evt);
            }
        });

        FlashFreqLBL1.setText("FlashFreq:");

        EditObjectPathBUT.setText("update Path");
        EditObjectPathBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditObjectPathBUTActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(EditObjectPathBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(strokeLBL1)
                            .addComponent(origXLBL1)
                            .addComponent(widthLBL1))
                        .addGap(4, 4, 4)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(EditOrigXTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(EditWidthTXT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(EditStrokeTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(10, 10, 10)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(origYLBL1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(heightLBL1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(FlashFreqLBL1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(angleLBL1, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(EditAngleTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(EditFlashFreqTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(EditHeightTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(EditOrigYTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(6, 6, 6))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(EditObjectPathBUT)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(EditOrigXTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(origXLBL1)
                    .addComponent(origYLBL1)
                    .addComponent(EditOrigYTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(EditWidthTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(widthLBL1)
                    .addComponent(heightLBL1)
                    .addComponent(EditHeightTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(EditStrokeTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(strokeLBL1)
                    .addComponent(FlashFreqLBL1)
                    .addComponent(EditFlashFreqTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(angleLBL1)
                    .addComponent(EditAngleTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        copyPathBUT.setText("copy path");
        copyPathBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyPathBUTActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 197, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(delStimBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(playPathBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(loopPathTOGBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(showPathTOGBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(flashStimTOGBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(loopAllBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(copyPathBUT, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(delStimBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(playPathBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loopPathTOGBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showPathTOGBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(flashStimTOGBUT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loopAllBUT))
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(copyPathBUT))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Stimulus sets"));

        avlbSetLIST.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(avlbSetLIST);

        setNameLBL.setText("setName:");

        avlbSetLBL.setText("available stimulus sets:");

        loadSetBUT.setText("load stimset");
        loadSetBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadSetBUTActionPerformed(evt);
            }
        });

        deleteStimBUT.setText("delete stimset");
        deleteStimBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteStimBUTActionPerformed(evt);
            }
        });

        saveSetBUT.setText("save stimset");
        saveSetBUT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSetBUTActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(avlbSetLBL)
                    .addComponent(setNameLBL)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(deleteStimBUT, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(saveSetBUT, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadSetBUT, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(setNameTXT))
                .addContainerGap(27, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(setNameLBL)
                .addGap(5, 5, 5)
                .addComponent(setNameTXT, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(avlbSetLBL)
                .addGap(5, 5, 5)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 165, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveSetBUT)
                    .addComponent(loadSetBUT))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteStimBUT))
        );

        jScrollPane3.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jScrollPane3.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        ExceptionTXTAREA.setEditable(false);
        ExceptionTXTAREA.setColumns(20);
        ExceptionTXTAREA.setForeground(new java.awt.Color(255, 0, 51));
        ExceptionTXTAREA.setLineWrap(true);
        ExceptionTXTAREA.setRows(5);
        ExceptionTXTAREA.setWrapStyleWord(true);
        jScrollPane3.setViewportView(ExceptionTXTAREA);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane3))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void recordPathTOGBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordPathTOGBUTActionPerformed
        StimFrame.setRecordMousePathEnabled(recordPathTOGBUT.isSelected());
    }//GEN-LAST:event_recordPathTOGBUTActionPerformed

    private void AddStimBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddStimBUTActionPerformed
        ExceptionTXTAREA.setText("");
        
        float origX = Float.parseFloat(origXTXT.getText()), origY  = Float.parseFloat(origYTXT.getText());
        float width = Float.parseFloat(widthTXT.getText()), height = Float.parseFloat(heightTXT.getText());
        
        PaintableObject objToAdd;
        switch (SelectStimCBOX.getSelectedIndex()) {
            case 0://"oval"
                objToAdd = new PaintableObject(NameTXT.getText(),new Ellipse2D.Double(),StimFrame,width,height);
                break;
            case 1://"rectangle"
                objToAdd = new PaintableObject(NameTXT.getText(),new Rectangle2D.Double(),StimFrame,width,height);
                break;
            case 2: //"horizontal Stripes"
                //Theoretically the PaintableGridObjects class could paint any shape in a grid, but we want to use it 
                // to create Stripes, so we do it directly with Rectangles. This grid idea started, because using 
                // the painting method to create stripes is computationally expensive
                objToAdd = new PaintableGridObject(NameTXT.getText(),new Rectangle2D.Double(),StimFrame,width,height,1,Integer.parseInt(prop1TXT.getText()));
                break;
            case 3: //"vertical Stripes"
                objToAdd = new PaintableGridObject(NameTXT.getText(),new Rectangle2D.Double(),StimFrame,width,height,Integer.parseInt(prop1TXT.getText()),1);
                break;
            case 4: //"random dot patch"
                objToAdd = new PaintableRandomPatch(NameTXT.getText(), new Ellipse2D.Double(),StimFrame,width,height);
                if(!prop1TXT.getText().equals("rand")){
                    ((PaintableRandomPatch)objToAdd).setNumberPatchObjects(Integer.parseInt(prop1TXT.getText()));
                    ((PaintableRandomPatch)objToAdd).setRandomObjectSize();
                    ((PaintableRandomPatch)objToAdd).setRandomObjectLocations(0f,1f);
                }
                if(!prop2TXT.getText().equals("rand") && !prop4TXT.getText().equals("rand")){
                    ((PaintableRandomPatch)objToAdd).setUniformObjectSize(Float.parseFloat(prop2TXT.getText()), Float.parseFloat(prop4TXT.getText()));
                }
                break;
            default: 
                //By default the object will be an ellipse. This is just to make errorHandling easier.
                ExceptionTXTAREA.setText("StimulusType not detected!");
                objToAdd = new PaintableObject(NameTXT.getText(),new Ellipse2D.Double(),StimFrame,width,height);
        }
        
        objToAdd.setRelativeXY(origX, origY);
        objToAdd.setFlashFreqHz(Integer.parseInt(FlashFreqTXT.getText()));
        objToAdd.setObjectPath(StimFrame.getMousePath());
        objToAdd.setStroke(Float.parseFloat(strokeTXT.getText()));
        objToAdd.setAngle(Double.parseDouble(angleTXT.getText()));

        switch (SelectPaintCBOX.getSelectedIndex()) {
            case 0://"no fill"
                objToAdd.setObjectColor(new Color(0,0,0,0)); //totaly transparent
                break;
            case 1://"black"
                objToAdd.setObjectColor(Color.black);
                break;
            case 2://"sine grating(vert)"
                objToAdd.setPaintGradient(Integer.parseInt(prop3TXT.getText()), 0f, 0f, 1f, 0f, Color.black);
                break;
            case 3://"sine grating(horz)"
                objToAdd.setPaintGradient(Integer.parseInt(prop3TXT.getText()), 0f, 0f, 0f, 1f, Color.black);
                break;
            case 4://"stripes(vert)"
                objToAdd.setPaintStripes(Integer.parseInt(prop3TXT.getText()), 0f, 0f, 1f, 0f, Color.black, /*new Color(0,0,0,0)*/Color.white);
                break;
            case 5://"stripes(horz)"
                objToAdd.setPaintStripes(Integer.parseInt(prop3TXT.getText()), 0f, 0f, 0f, 1f, Color.black, /*new Color(0,0,0,0)*/Color.white);
                break;
            case 6:
                
            default: ExceptionTXTAREA.setText("PaintType not detected");    
        }

        addObjectToFrameAndList(objToAdd);
        recordPathTOGBUT.setSelected(false);
    }//GEN-LAST:event_AddStimBUTActionPerformed

    private void SelectStimCBOXActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SelectStimCBOXActionPerformed
        prop1LBL.setVisible(false);
        prop1TXT.setVisible(false);
        prop2LBL.setVisible(false);
        prop2TXT.setVisible(false);
        prop4LBL.setVisible(false);
        prop4TXT.setVisible(false);
        switch (SelectStimCBOX.getSelectedIndex()) {
            //This method of switching allows to use two textboxes as variable
            // input if needed by more ellaborate stimuli
            case 0:
            case 1:
                strokeTXT.setText("4");
                SelectPaintCBOX.getModel().setSelectedItem("no fill");
                break;
            case 2:
                prop1LBL.setText("number columns:");
                prop1TXT.setText("1");
                prop1LBL.setVisible(true);
                prop1TXT.setVisible(true);
                strokeTXT.setText("0");
                SelectPaintCBOX.getModel().setSelectedItem("black");
                break;
            case 3:
                prop1LBL.setText("number rows:");
                prop1TXT.setText("1");
                prop1LBL.setVisible(true);
                prop1TXT.setVisible(true);
                strokeTXT.setText("0");
                SelectPaintCBOX.getModel().setSelectedItem("black");
                break;
            case 4:
                prop1LBL.setText("number objects:");
                prop1TXT.setText("rand");
                prop1LBL.setVisible(true);
                prop1TXT.setVisible(true);
                prop2LBL.setText("object width:");
                prop2TXT.setText("rand");
                prop2LBL.setVisible(true);
                prop2TXT.setVisible(true);
                prop4LBL.setText("object height:");
                prop4TXT.setText("rand");
                prop4LBL.setVisible(true);
                prop4TXT.setVisible(true);
                SelectPaintCBOX.getModel().setSelectedItem("black");
                strokeTXT.setText("0");
                break;
            default: ExceptionTXTAREA.setText("StimulusType not detected");        
        }
    }//GEN-LAST:event_SelectStimCBOXActionPerformed

    private void avlbStimLISTValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_avlbStimLISTValueChanged
        if(avlbStimLIST.getSelectedValue() == null) {
            //This means that the current selected stim has been deleted. Reset all the buttons
            showPathTOGBUT.setSelected(false);
            loopPathTOGBUT.setSelected(false);
            flashStimTOGBUT.setSelected(false);
            return;
        }
        
        PaintableObject foo = getCurrentListedStimulus();
        showPathTOGBUT.setSelected(foo.isRequestPathPaintEnabled());
        loopPathTOGBUT.setSelected(foo.isPathLoop());
        flashStimTOGBUT.setSelected(foo.isFlashEnabled());
        
        EditAngleTXT.setText(String.valueOf(foo.getAngle()));
        EditFlashFreqTXT.setText(String.valueOf(foo.getFlashFreqHz()));
        EditHeightTXT.setText(String.valueOf(foo.getRelativeHeight()));
        EditWidthTXT.setText(String.valueOf(foo.getRelativeWidth()));
        EditOrigXTXT.setText(String.valueOf(foo.getRelativeX()));
        EditOrigYTXT.setText(String.valueOf(foo.getRelativeY()));
        EditStrokeTXT.setText(String.valueOf(foo.getStroke()));
    }//GEN-LAST:event_avlbStimLISTValueChanged

    private void showPathTOGBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPathTOGBUTActionPerformed
        if(avlbStimLIST.getSelectedValue() == null) {
            showPathTOGBUT.setSelected(false);
            return;
        }
        if(showPathTOGBUT.isSelected()){
            getCurrentListedStimulus().setRequestPathPaintEnabled(true);
        } else {
            getCurrentListedStimulus().setRequestPathPaintEnabled(false);
        }
        StimFrame.repaint();
    }//GEN-LAST:event_showPathTOGBUTActionPerformed

    private void delStimBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_delStimBUTActionPerformed
        if(avlbStimLIST.getSelectedValue() == null) return;
        int index = getCurrentListedStimulusIndex();
        
        if(loopPathTOGBUT.isSelected()) getCurrentListedStimulus().playPathCancel();
        if(flashStimTOGBUT.isSelected()) getCurrentListedStimulus().stopFlashing();
        
        StimFrame.removeObject(index);
        rebuiltStimList();
        if(!(avlbStimLIST.getModel().getSize() == 0)) avlbStimLIST.setSelectedIndex(avlbStimLIST.getModel().getSize()-1);
    }//GEN-LAST:event_delStimBUTActionPerformed

    private void playPathBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playPathBUTActionPerformed
        if(avlbStimLIST.getSelectedValue() == null) return;
        getCurrentListedStimulus().playPathOnce();
    }//GEN-LAST:event_playPathBUTActionPerformed

    private void loopPathTOGBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loopPathTOGBUTActionPerformed
        if(avlbStimLIST.getSelectedValue() == null) return;
        getCurrentListedStimulus().playPathLoopToggle();   
    }//GEN-LAST:event_loopPathTOGBUTActionPerformed

    private void flashStimTOGBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flashStimTOGBUTActionPerformed
        if(avlbStimLIST.getSelectedValue() == null) return;
        if(flashStimTOGBUT.isSelected()){
            getCurrentListedStimulus().startFlashing();
        } else {
            getCurrentListedStimulus().stopFlashing();
        } 
    }//GEN-LAST:event_flashStimTOGBUTActionPerformed

    private void FlashFreqTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FlashFreqTXTActionPerformed
        if(Integer.parseInt(FlashFreqTXT.getText()) < 1){
            FlashFreqTXT.setText("1");
        } else if(Integer.parseInt(FlashFreqTXT.getText()) > 100){
            FlashFreqTXT.setText("100");
        }
    }//GEN-LAST:event_FlashFreqTXTActionPerformed

    private void saveSetBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSetBUTActionPerformed
        if(setNameTXT.getText() == null | setNameTXT.getText().equals("")) {
            setNameTXT.requestFocus();
            return;
        }
        ExceptionTXTAREA.setText("");
        
        // We cant save this to preferences, as when the objects have a path attached
        // the byteStream would be far to large. Hence we write to a file.
        File writeFile = new File(System.getProperty("user.home")+"/"+FILE_NAME_PREFIX+setNameTXT.getText()+".txt");
        if (writeFile.exists()) writeFile.delete();
        
        
        try (FileOutputStream fileOut = new FileOutputStream(writeFile); 
             ObjectOutputStream oos = new ObjectOutputStream(fileOut);) 
        {
            writeFile.createNewFile();
            oos.writeObject(StimFrame.getObjectList()); 
        } catch(IOException ex) {
            ExceptionTXTAREA.setText(ex.getMessage());
            return; 
        }
        listModelSet.addElement(setNameTXT.getText());
        avlbSetLIST.setSelectedIndex(listModelSet.indexOf(setNameTXT.getText()));
        setNameTXT.setText("");
    }//GEN-LAST:event_saveSetBUTActionPerformed

    private void loadSetBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadSetBUTActionPerformed
        String loadFile;
        ExceptionTXTAREA.setText("");
        //If no loadString is entered and no know set is selected we can not load
        if((setNameTXT.getText() == null | setNameTXT.getText().equals("")) && (avlbSetLIST.getSelectedIndex() == -1)) {
            setNameTXT.requestFocus();
            return;
        } else if(!(setNameTXT.getText() == null | setNameTXT.getText().equals(""))) {
            //If a fileName is entered in the textbox we load that instead of a selected element from the list
            avlbSetLIST.clearSelection();
            loadFile = setNameTXT.getText();
        } else {
            setNameTXT.setText("");
            loadFile = (String) avlbSetLIST.getSelectedValue();
        }
        
        ArrayList<PaintableObject> objectImportList;
        try (FileInputStream fileIn = new FileInputStream(System.getProperty("user.home")+"/"+FILE_NAME_PREFIX+loadFile+".txt");
             ObjectInputStream in = new ObjectInputStream(fileIn)) 
        {
            objectImportList = (ArrayList<PaintableObject>) in.readObject();

            for( PaintableObject o : objectImportList) {
                o.setCanavas(StimFrame);
                if(o.isPathLoop())o.playPathLoopToggle();
                if(o.isFlashEnabled())o.stopFlashing();
                addObjectToFrameAndList(o);
            }
        } catch(IOException | ClassNotFoundException ex){
           ExceptionTXTAREA.setText(ex.getMessage());
        }
    }//GEN-LAST:event_loadSetBUTActionPerformed

    private void deleteStimBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteStimBUTActionPerformed
        int itemIndex = avlbSetLIST.getSelectedIndex();
        if(itemIndex == -1) return;
        File writeFile = new File(System.getProperty("user.home")+"/"+FILE_NAME_PREFIX+((String) avlbSetLIST.getSelectedValue())+".txt");
        if (writeFile.exists()) writeFile.delete();

        listModelSet.remove(itemIndex); //If the file does not exist we delete the item from the list,as it is there only by error.
        avlbSetLIST.setSelectedIndex(listModelSet.getSize()-1);
    }//GEN-LAST:event_deleteStimBUTActionPerformed

    private void SelectPaintCBOXActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SelectPaintCBOXActionPerformed
        ExceptionTXTAREA.setText("");
        prop3LBL.setVisible(false);
        prop3TXT.setVisible(false);
        switch (SelectPaintCBOX.getSelectedIndex()) {
            case 0:
            case 1:
                break;
            case 2:
            case 3:
                prop3LBL.setVisible(true);
                prop3TXT.setVisible(true);
                prop3LBL.setText("#Paintgratings:");
                prop3TXT.setText("1");
                break;
            case 4:
            case 5:
                prop3LBL.setVisible(true);
                prop3TXT.setVisible(true);
                prop3LBL.setText("#Paintstripes:");
                prop3TXT.setText("1");
                break;
            default: ExceptionTXTAREA.setText("PaintType not detected");
        }
    }//GEN-LAST:event_SelectPaintCBOXActionPerformed

    private void linPathBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linPathBUTActionPerformed
        if(!pathDesigner.isShowing()){
            pathDesigner.setVisible(true);
            pathDesigner.requestFocus();
        }
    }//GEN-LAST:event_linPathBUTActionPerformed

    private void loopAllBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loopAllBUTActionPerformed
        ArrayList<PaintableObject> list = StimFrame.getObjectList();
        
        for(PaintableObject p : list) {
            if(p.isPathLoop()) continue;
            p.playPathLoopToggle();
        } 
        loopPathTOGBUT.setSelected(getCurrentListedStimulus().isPathLoop());
    }//GEN-LAST:event_loopAllBUTActionPerformed

    private void EditFlashFreqTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditFlashFreqTXTActionPerformed
        int flashFreqToSet = Integer.parseInt(EditFlashFreqTXT.getText());
        if(flashFreqToSet < 1){
            EditFlashFreqTXT.setText("1");
            flashFreqToSet = 1;
        } else if(flashFreqToSet > 100){
            EditFlashFreqTXT.setText("100");
            flashFreqToSet = 100;
        }
        getCurrentListedStimulus().setFlashFreqHz(flashFreqToSet);
    }//GEN-LAST:event_EditFlashFreqTXTActionPerformed

    private void EditOrigXTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditOrigXTXTActionPerformed
        getCurrentListedStimulus().setRelativeX(Float.parseFloat(EditOrigXTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditOrigXTXTActionPerformed

    private void EditOrigYTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditOrigYTXTActionPerformed
        getCurrentListedStimulus().setRelativeY(Float.parseFloat(EditOrigYTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditOrigYTXTActionPerformed

    private void EditWidthTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditWidthTXTActionPerformed
        getCurrentListedStimulus().setRelativeWidth(Float.parseFloat(EditWidthTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditWidthTXTActionPerformed

    private void EditHeightTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditHeightTXTActionPerformed
        getCurrentListedStimulus().setRelativeHeight(Float.parseFloat(EditHeightTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditHeightTXTActionPerformed

    private void EditStrokeTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditStrokeTXTActionPerformed
        getCurrentListedStimulus().setStroke(Float.parseFloat(EditStrokeTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditStrokeTXTActionPerformed

    private void EditAngleTXTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditAngleTXTActionPerformed
        getCurrentListedStimulus().setAngle(Double.parseDouble(EditAngleTXT.getText()));
        StimFrame.repaint();
    }//GEN-LAST:event_EditAngleTXTActionPerformed

    private void EditObjectPathBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditObjectPathBUTActionPerformed
        getCurrentListedStimulus().setObjectPath(StimFrame.getMousePath());
        StimFrame.getMousePath().clear();
        StimFrame.repaint();
    }//GEN-LAST:event_EditObjectPathBUTActionPerformed

    private void copyPathBUTActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyPathBUTActionPerformed
       StimFrame.setMousePath(getCurrentListedStimulus().getObjectPath());
       StimFrame.repaint();
    }//GEN-LAST:event_copyPathBUTActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddStimBUT;
    private javax.swing.JTextField EditAngleTXT;
    private javax.swing.JTextField EditFlashFreqTXT;
    private javax.swing.JTextField EditHeightTXT;
    private javax.swing.JButton EditObjectPathBUT;
    private javax.swing.JTextField EditOrigXTXT;
    private javax.swing.JTextField EditOrigYTXT;
    private javax.swing.JTextField EditStrokeTXT;
    private javax.swing.JTextField EditWidthTXT;
    private javax.swing.JTextArea ExceptionTXTAREA;
    private javax.swing.JLabel FlashFreqLBL;
    private javax.swing.JLabel FlashFreqLBL1;
    private javax.swing.JTextField FlashFreqTXT;
    private javax.swing.JTextField NameTXT;
    private javax.swing.JComboBox SelectPaintCBOX;
    private javax.swing.JComboBox SelectStimCBOX;
    private javax.swing.JLabel angleLBL;
    private javax.swing.JLabel angleLBL1;
    private javax.swing.JTextField angleTXT;
    private javax.swing.JLabel avlbSetLBL;
    private javax.swing.JList avlbSetLIST;
    private javax.swing.JList avlbStimLIST;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JButton copyPathBUT;
    private javax.swing.JButton delStimBUT;
    private javax.swing.JButton deleteStimBUT;
    private javax.swing.JToggleButton flashStimTOGBUT;
    private javax.swing.JLabel heightLBL;
    private javax.swing.JLabel heightLBL1;
    private javax.swing.JTextField heightTXT;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton linPathBUT;
    private javax.swing.JButton loadSetBUT;
    private javax.swing.JButton loopAllBUT;
    private javax.swing.JToggleButton loopPathTOGBUT;
    private javax.swing.JLabel nameLBL;
    private javax.swing.JLabel origXLBL;
    private javax.swing.JLabel origXLBL1;
    private javax.swing.JTextField origXTXT;
    private javax.swing.JLabel origYLBL;
    private javax.swing.JLabel origYLBL1;
    private javax.swing.JTextField origYTXT;
    private javax.swing.JButton playPathBUT;
    private javax.swing.JLabel prop1LBL;
    private javax.swing.JTextField prop1TXT;
    private javax.swing.JLabel prop2LBL;
    private javax.swing.JTextField prop2TXT;
    private javax.swing.JLabel prop3LBL;
    private javax.swing.JTextField prop3TXT;
    private javax.swing.JLabel prop4LBL;
    private javax.swing.JTextField prop4TXT;
    private javax.swing.JToggleButton recordPathTOGBUT;
    private javax.swing.JButton saveSetBUT;
    private javax.swing.JLabel setNameLBL;
    private javax.swing.JTextField setNameTXT;
    private javax.swing.JToggleButton showPathTOGBUT;
    private javax.swing.JLabel strokeLBL;
    private javax.swing.JLabel strokeLBL1;
    private javax.swing.JTextField strokeTXT;
    private javax.swing.JLabel widthLBL;
    private javax.swing.JLabel widthLBL1;
    private javax.swing.JTextField widthTXT;
    // End of variables declaration//GEN-END:variables


}
