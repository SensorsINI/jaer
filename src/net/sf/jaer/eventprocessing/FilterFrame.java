/*
 * FilterFrame.java
 *
 * Created on October 31, 2005, 8:29 PM
 */
package net.sf.jaer.eventprocessing;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import net.sf.jaer.JaerConstants;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.JAERWindowUtilities;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.XMLFileFilter;

/**
 * This JFrame holds all the event processing controls. It also allows global
 * measurement of filter performance and allows setting a flag that determines
 * of the filters process events on the rendering or data acquisition cycle.
 * Export and import of filter preferences are also possible.
 *
 * @author tobi
 */
public class FilterFrame<PanelType extends FilterPanel> extends javax.swing.JFrame implements PropertyChangeListener/*, WindowSaver.DontResize*/ {

    // tobi commented out DontResize because the filter frame was extending below the bottom of screen, making it awkward to control properties for deep implementations
    final int MAX_ROWS = 10; // max rows of filters, then wraps back to top
    static Preferences prefs = Preferences.userNodeForPackage(FilterFrame.class);
    Logger log = Logger.getLogger("filter");
    AEChip chip;
    FilterChain filterChain;
    RecentFiles recentFiles = null;
    private boolean restoreFilterEnabledStateEnabled;
    private String defaultFolder = null;
    EngineeringFormat engFmt = new EngineeringFormat();
    /**
     * Key for preferences of last selected filter; used to reselect this filter
     * on startup.
     */
    public static final String LAST_FILTER_SELECTED_KEY = "FilterFrame.lastFilterSelected";
    private JButton resetStatisticsButton = null;
    private Border selectedBorder = new LineBorder(Color.red);

    /**
     * Creates new form FilterFrame
     */
    public FilterFrame(AEChip chip) {
        this.chip = chip;
        this.filterChain = chip.getFilterChain();
        chip.setFilterFrame(this);
        setName("FilterFrame");
        initComponents();
        setIconImage(new javax.swing.ImageIcon(getClass().getResource(JaerConstants.ICON_IMAGE_FILTERS)).getImage());

//        fileMenu.remove(prefsEditorMenuItem); // TODO tobi hack to work around leftover item in form that was edited outside of netbeans
        rebuildContents();
        // from http://stackoverflow.com/questions/11533162/how-to-prevent-jscrollpane-from-scrolling-when-arrow-keys-are-pressed
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // from http://stackoverflow.com/questions/5583495/how-do-i-speed-up-the-scroll-speed-in-a-jscrollpane-when-using-the-mouse-wheel
        scrollPane.getActionMap().put("unitScrollDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        });
        scrollPane.getActionMap().put("unitScrollUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        });
        setRestoreFilterEnabledStateEnabled(prefs.getBoolean("FilterFrame.restoreFilterEnabledStateEnabled", true)); // sets the menu item state
        if (chip != null) {
            setTitle(chip.getName() + " - filters");
        }
        switch (filterChain.getProcessingMode()) {
            case RENDERING:
                renderingModeMenuItem.setSelected(true);
                break;
            case ACQUISITION:
                acquisitionModeMenuItem.setSelected(true);
                break;
            default:

        }
        if (filterChain != null) {
            filterChain.setMeasurePerformanceEnabled(measurePerformanceCheckBoxMenuItem.isSelected());
        }
        // recent files tracks recently used files *and* folders. recentFiles adds the anonymous listener
        // built here to open the selected file
        recentFiles = new RecentFiles(prefs, fileMenu, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt) {
                File f = new File(evt.getActionCommand());
                log.info("opening " + evt.getActionCommand());
                try {
                    if ((f != null) && f.isFile()) {
                        loadFile(f);
                    } else if ((f != null) && f.isDirectory()) {
                        prefs.put("FilterFrame.lastFile", f.getCanonicalPath());
                        loadMenuItemActionPerformed(null);
                    }
                } catch (Exception fnf) {
                    fnf.printStackTrace();
                    recentFiles.removeFile(f);
                }
            }
        });

        // now set state of all filters enabled
        if (restoreFilterEnabledStateEnabled) {
            //            log.info("Restoring filter enabled setting for each filter");

            for (EventFilter f : filterChain) {
                f.setPreferredEnabledState();
                //                boolean yes=prefs.getBoolean(f.prefsEnabledKey(),false);
                //                if(yes) log.info("enabling "+f);
                //                f.setFilterEnabled(yes);
            }
        }
        pack();

        defaultFolder = System.getProperty("user.dir");
        try {
            File f = new File(defaultFolder + File.separator + "filterSettings");
            defaultFolder = f.getPath();
            log.info("default filter settings file path is " + defaultFolder);
        } catch (Exception e) {
            log.warning("could not locate default folder for filter settings relative to starting folder, using startup folder");
        }
        //        log.info("defaultFolder="+defaultFolder);
        updateIntervalField.setText(engFmt.format(filterChain.getUpdateIntervalMs()));

        String lastFilter = chip.getPrefs().get(LAST_FILTER_SELECTED_KEY, null);
        if (lastFilter != null) {
            for (FilterPanel f : filterPanels) {
                if (f.getFilter().getClass().toString().equals(lastFilter)) {
                    log.info("making settings visible for last filter " + f.getFilter());
                    f.setControlsVisible(true);
                }
            }
        }
    }

    private void prefsEditorMenuItemActionPerformed(ActionEvent evt) {
        // only added to handle leftover prefs editor that was removed by Luca without removing from netbeans FORM
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        modeButtonGroup = new javax.swing.ButtonGroup();
        toolBar1 = new javax.swing.JToolBar();
        disableFilteringToggleButton = new javax.swing.JToggleButton();
        resetAllButton = new javax.swing.JButton();
        overviewButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        updateIntervalLabel = new javax.swing.JLabel();
        updateIntervalField = new javax.swing.JTextField();
        jbuttonSelectFilt = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        scrollPane = new javax.swing.JScrollPane();
        filtersPanel = new javax.swing.JPanel();
        highlightTF = new javax.swing.JTextField();
        mainMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        loadMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        customizeMenuItem = new javax.swing.JMenuItem();
        highlightMI = new javax.swing.JMenuItem();
        modeMenu = new javax.swing.JMenu();
        renderingModeMenuItem = new javax.swing.JRadioButtonMenuItem();
        acquisitionModeMenuItem = new javax.swing.JRadioButtonMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        measurePerformanceCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        resetPerformanceMeasurementMI = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        restoreFilterEnabledStateCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        helpMenu = new javax.swing.JMenu();
        jaerFilterHelpMI = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("FilterControl");
        setMinimumSize(new java.awt.Dimension(150, 37));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentMoved(java.awt.event.ComponentEvent evt) {
                formComponentMoved(evt);
            }
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        disableFilteringToggleButton.setText("Disable all");
        disableFilteringToggleButton.setToolTipText("Temporarily disables all filters");
        disableFilteringToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                disableFilteringToggleButtonActionPerformed(evt);
            }
        });
        toolBar1.add(disableFilteringToggleButton);

        resetAllButton.setText("Reset  all");
        resetAllButton.setToolTipText("Resets all filters in chain that are enabled");
        resetAllButton.setFocusable(false);
        resetAllButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        resetAllButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        resetAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAllButtonActionPerformed(evt);
            }
        });
        toolBar1.add(resetAllButton);

        overviewButton.setText("Overview");
        overviewButton.setToolTipText("Shows overview of all filters");
        overviewButton.setFocusable(false);
        overviewButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        overviewButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        overviewButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overviewButtonActionPerformed(evt);
            }
        });
        toolBar1.add(overviewButton);

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.LINE_AXIS));

        updateIntervalLabel.setText("updateIntevalMs");
        jPanel1.add(updateIntervalLabel);

        updateIntervalField.setColumns(8);
        updateIntervalField.setToolTipText("Sets the maximum update interval for filters that notify observers");
        updateIntervalField.setMaximumSize(new java.awt.Dimension(50, 2147483647));
        updateIntervalField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateIntervalFieldActionPerformed(evt);
            }
        });
        jPanel1.add(updateIntervalField);

        toolBar1.add(jPanel1);

        jbuttonSelectFilt.setText("Select Filters...");
        jbuttonSelectFilt.setToolTipText("Opens dialog to select loaded filters");
        jbuttonSelectFilt.setFocusable(false);
        jbuttonSelectFilt.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jbuttonSelectFilt.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jbuttonSelectFilt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbuttonSelectFiltActionPerformed(evt);
            }
        });
        toolBar1.add(jbuttonSelectFilt);

        jLabel1.setText("<html>Enabled filters (check box selected) <br>are processed from top to bottom");

        filtersPanel.setMaximumSize(new java.awt.Dimension(0, 0));
        filtersPanel.setMinimumSize(new java.awt.Dimension(100, 300));
        filtersPanel.setLayout(new javax.swing.BoxLayout(filtersPanel, javax.swing.BoxLayout.Y_AXIS));
        scrollPane.setViewportView(filtersPanel);

        highlightTF.setText("highlight...");
        highlightTF.setToolTipText("highlight filters/parameters");
        highlightTF.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                highlightTFFocusGained(evt);
            }
        });
        highlightTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightTFActionPerformed(evt);
            }
        });
        highlightTF.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                highlightTFKeyPressed(evt);
            }
        });

        fileMenu.setMnemonic('f');
        fileMenu.setText("File");

        loadMenuItem.setMnemonic('l');
        loadMenuItem.setText("Load settings...");
        loadMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loadMenuItem);

        saveAsMenuItem.setText("Save settings as...");
        saveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveAsMenuItem);
        fileMenu.add(jSeparator2);

        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        mainMenuBar.add(fileMenu);

        viewMenu.setMnemonic('v');
        viewMenu.setText("View");

        customizeMenuItem.setMnemonic('c');
        customizeMenuItem.setText("Select Filters...");
        customizeMenuItem.setToolTipText("Choose the filters you want to see");
        customizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customizeMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(customizeMenuItem);

        highlightMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, java.awt.event.InputEvent.CTRL_MASK));
        highlightMI.setMnemonic('h');
        highlightMI.setText("Highlight...");
        highlightMI.setToolTipText("focuses the highlight text field, to allow highlighting filters or properties");
        highlightMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightMIActionPerformed(evt);
            }
        });
        viewMenu.add(highlightMI);

        mainMenuBar.add(viewMenu);

        modeMenu.setMnemonic('o');
        modeMenu.setText("Options");
        modeMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                modeMenuMenuSelected(evt);
            }
        });

        modeButtonGroup.add(renderingModeMenuItem);
        renderingModeMenuItem.setMnemonic('r');
        renderingModeMenuItem.setSelected(true);
        renderingModeMenuItem.setText("Process on rendering cycle");
        renderingModeMenuItem.setToolTipText("Process events on rendering cycle");
        renderingModeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renderingModeMenuItemActionPerformed(evt);
            }
        });
        modeMenu.add(renderingModeMenuItem);

        modeButtonGroup.add(acquisitionModeMenuItem);
        acquisitionModeMenuItem.setMnemonic('a');
        acquisitionModeMenuItem.setText("Process on acqusition cycle");
        acquisitionModeMenuItem.setToolTipText("Process events on hardware data acquisition cycle");
        acquisitionModeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acquisitionModeMenuItemActionPerformed(evt);
            }
        });
        modeMenu.add(acquisitionModeMenuItem);
        modeMenu.add(jSeparator1);

        measurePerformanceCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
        measurePerformanceCheckBoxMenuItem.setMnemonic('p');
        measurePerformanceCheckBoxMenuItem.setText("Measure filter processing time");
        measurePerformanceCheckBoxMenuItem.setToolTipText("Enables instrumentation of filter performance. Filter processing time statistics are printed to System.out. They appear in netbeans IDE console, for example, but not in built-in jAER console, which does not show System.out.");
        measurePerformanceCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                measurePerformanceCheckBoxMenuItemActionPerformed(evt);
            }
        });
        modeMenu.add(measurePerformanceCheckBoxMenuItem);

        resetPerformanceMeasurementMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        resetPerformanceMeasurementMI.setMnemonic('s');
        resetPerformanceMeasurementMI.setText("Reset performance measurement statistics");
        resetPerformanceMeasurementMI.setToolTipText("Resets the statsitics after next processing cycle");
        resetPerformanceMeasurementMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetPerformanceMeasurementMIActionPerformed(evt);
            }
        });
        modeMenu.add(resetPerformanceMeasurementMI);
        modeMenu.add(jSeparator3);

        restoreFilterEnabledStateCheckBoxMenuItem.setText("Restore filter enabled state");
        restoreFilterEnabledStateCheckBoxMenuItem.setToolTipText("If enabled, filter enabled state is restored on startup");
        restoreFilterEnabledStateCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoreFilterEnabledStateCheckBoxMenuItemActionPerformed(evt);
            }
        });
        modeMenu.add(restoreFilterEnabledStateCheckBoxMenuItem);

        mainMenuBar.add(modeMenu);

        helpMenu.setMnemonic('h');
        helpMenu.setText("Help");

        jaerFilterHelpMI.setText("jAER home");
        jaerFilterHelpMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jaerFilterHelpMIActionPerformed(evt);
            }
        });
        helpMenu.add(jaerFilterHelpMI);

        mainMenuBar.add(helpMenu);

        setJMenuBar(mainMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(highlightTF)
                .addContainerGap())
            .addComponent(scrollPane)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 218, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(highlightTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 476, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
            JAERWindowUtilities.constrainFrameSizeToScreenSize(this); // constrain to screen
	}//GEN-LAST:event_formComponentResized

	private void disableFilteringToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableFilteringToggleButtonActionPerformed
            filterChain.setFilteringEnabled(!disableFilteringToggleButton.isSelected());
	}//GEN-LAST:event_disableFilteringToggleButtonActionPerformed

	private void customizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customizeMenuItemActionPerformed
            filterChain.customize();
	}//GEN-LAST:event_customizeMenuItemActionPerformed

	private void restoreFilterEnabledStateCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restoreFilterEnabledStateCheckBoxMenuItemActionPerformed
            setRestoreFilterEnabledStateEnabled(restoreFilterEnabledStateCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_restoreFilterEnabledStateCheckBoxMenuItemActionPerformed

    private void setModeMenuEnabled() {
        // set the acquisition processing mode filter setting enabled only if we are live
        switch (chip.getAeViewer().getPlayMode()) {
            case LIVE:
                acquisitionModeMenuItem.setEnabled(true);
                break;
            default:
                acquisitionModeMenuItem.setEnabled(false);
        }
    }

    // sets the acquisition mode filtering menu item enabled depending on whether device is attached.
	private void modeMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_modeMenuMenuSelected
            setModeMenuEnabled();
	}//GEN-LAST:event_modeMenuMenuSelected

	private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
            System.exit(0);
	}//GEN-LAST:event_exitMenuItemActionPerformed

        
	private void measurePerformanceCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_measurePerformanceCheckBoxMenuItemActionPerformed
            filterChain.setMeasurePerformanceEnabled(measurePerformanceCheckBoxMenuItem.isSelected());
            if (measurePerformanceCheckBoxMenuItem.isSelected()) {
                if (resetStatisticsButton == null) {
                    resetStatisticsButton = new JButton(new ResetPerformanceStatisticsAction());
                }
                toolBar1.add(resetStatisticsButton);
                
            } else if (resetStatisticsButton != null) {
                toolBar1.remove(resetStatisticsButton);
                validate();
            }
	}//GEN-LAST:event_measurePerformanceCheckBoxMenuItemActionPerformed

	private void acquisitionModeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acquisitionModeMenuItemActionPerformed
            filterChain.setProcessingMode(FilterChain.ProcessingMode.ACQUISITION);
	}//GEN-LAST:event_acquisitionModeMenuItemActionPerformed

	private void renderingModeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renderingModeMenuItemActionPerformed
            filterChain.setProcessingMode(FilterChain.ProcessingMode.RENDERING);
	}//GEN-LAST:event_renderingModeMenuItemActionPerformed

    /**
     * renews contents by newing all filters, thus filling them with preference
     * values. This is how preferences can replace values without using
     * extensive preference change listeners
     */
    public void renewContents() {
        filterChain.renewChain();
        filterChain.contructPreferredFilters();
        rebuildContents();

    }
    // list of individual filter panels
    protected ArrayList<PanelType> filterPanels = new ArrayList();

    /**
     * rebuilds the frame contents using the existing filters in the filterChain
     */
    public void rebuildContents() {
        filterPanels.clear();
        filtersPanel.removeAll();
        int n = 0;
        int w = 100, h = 30;
        //        log.info("rebuilding FilterFrame for chip="+chip);
        //        if(true){ //(filterChain.size()<=MAX_ROWS){
        //            filtersPanel.setLayout(new BoxLayout(filtersPanel,BoxLayout.Y_AXIS));
        //            filtersPanel.removeAll();
        for (EventFilter2D f : filterChain) {
            FilterPanel p = new FilterPanel(f);
            filtersPanel.add(p);
            filterPanels.add((PanelType) p);
            n++;
            h += p.getHeight();
            w = p.getWidth();
        }
        //            pack();
        pack();
        //        else{
        //            // multi column layout
        //            scrollPane.removeAll();
        //            scrollPane.setLayout(new BoxLayout(scrollPane, BoxLayout.X_AXIS));
        //            int filterNumber=0;
        //            JPanel panel=null;
        //
        //            for(EventFilter2D f:filterChain){
        //                if(filterNumber%MAX_ROWS==0){
        //                    panel=new JPanel();
        //                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        //                    scrollPane.add(panel);
        //                }
        //                FilterPanel p=new FilterPanel(f);
        //                panel.add(p);
        //                filterPanels.add(p);
        ////                if((filterNumber+1)%MAX_ROWS==0){
        ////                         pad last panel with box filler at botton
        ////                        panel.add(Box.createVerticalGlue());
        ////                        System.out.println("filterNumber="+filterNumber);
        ////                }
        //                filterNumber++;
        //            }
        //            pack();
        //        }
    }
    File lastFile;

	private void loadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadMenuItemActionPerformed
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = prefs.get("FilterFrame.lastFile", defaultFolder); // TODO seems to be same as for biases, should default to filterSettings folder of jAER
            lastFile = new File(lastFilePath);
            if (!lastFile.exists()) {
                log.warning("last file for filter settings " + lastFile + " does not exist, using " + defaultFolder);
                lastFile = new File(defaultFolder);
            }

            XMLFileFilter fileFilter = new XMLFileFilter();
            fileChooser.addChoosableFileFilter(fileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
            int retValue = fileChooser.showOpenDialog(this);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                File f = fileChooser.getSelectedFile();
                loadFile(f);
            }
	}//GEN-LAST:event_loadMenuItemActionPerformed

    public void loadFile(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            Preferences.importPreferences(fis);  // we import the tree into *this* preference node, which is not the one exported (which is root node)
            prefs.put("FilterFrame.lastFile", f.getCanonicalPath());
            log.info("imported preferences from " + f);
            recentFiles.addFile(f);
            renewContents();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRestoreFilterEnabledStateEnabled() {
        return restoreFilterEnabledStateEnabled;
    }

    public void setRestoreFilterEnabledStateEnabled(boolean restoreFilterEnabledStateEnabled) {
        this.restoreFilterEnabledStateEnabled = restoreFilterEnabledStateEnabled;
        prefs.putBoolean("FilterFrame.restoreFilterEnabledStateEnabled", restoreFilterEnabledStateEnabled);
        restoreFilterEnabledStateCheckBoxMenuItem.setSelected(restoreFilterEnabledStateEnabled);
    }

	private void saveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsMenuItemActionPerformed
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = prefs.get("FilterFrame.lastFile", defaultFolder); // getString the last folder
            lastFile = new File(lastFilePath);
            XMLFileFilter fileFilter = new XMLFileFilter();
            fileChooser.addChoosableFileFilter(fileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
            fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            fileChooser.setDialogTitle("Save filter settings to");
            fileChooser.setMultiSelectionEnabled(false);
            //            if(lastImageFile==null){
            //                lastImageFile=new File("snapshot.png");
            //            }
            //            fileChooser.setSelectedFile(lastImageFile);
            int retValue = fileChooser.showSaveDialog(this);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fileChooser.getSelectedFile();
                    String suffix = "";
                    if (!file.getName().endsWith(".xml")) {
                        suffix = ".xml";
                    }
                    file = new File(file.getPath() + suffix);
                    // examine prefs for filters
                    //                String path=null;
                    //                for(EventFilter f:filterChain){
                    //                    Preferences p=f.getPrefs();
                    //                    path=p.absolutePath();
                    ////                    System.out.println("filter "+f+" has prefs node name="+p.name()+" and absolute path="+p.absolutePath());
                    //                }

                    //                Preferences prefs=Preferences.userNodeForPackage(JAERViewer.class); // exports absolutely everything, which is not so good
                    if (filterChain.size() == 0) {
                        log.warning("no filters to export");
                        return;
                    }
                    Preferences chipPrefs = filterChain.get(0).getPrefs(); // assume all filters have same prefs node (derived from chip class)
                    FileOutputStream fos = new FileOutputStream(file);
                    chipPrefs.exportSubtree(fos);
                    log.info("exported prefs subtree " + chipPrefs.absolutePath() + " to file " + file);
                    fos.close();
                    recentFiles.addFile(file);
                    prefs.put("FilterFrame.lastFile", file.getCanonicalPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
	}//GEN-LAST:event_saveAsMenuItemActionPerformed

	private void formComponentMoved(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentMoved
            //        JAERWindowUtilities.constrainFrameSizeToScreenSize(this);
	}//GEN-LAST:event_formComponentMoved

	private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
            filterChain.cleanup();
	}//GEN-LAST:event_formWindowClosed

	private void updateIntervalFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateIntervalFieldActionPerformed
            try {
                float intvl = engFmt.parseFloat(updateIntervalField.getText());
                filterChain.setUpdateIntervalMs(intvl);
                updateIntervalField.setText(engFmt.format(intvl));
                log.info("set global event filter update interval to " + updateIntervalField.getText());
            } catch (Exception e) {
                updateIntervalField.selectAll();
                log.warning(e.toString());
            }
	}//GEN-LAST:event_updateIntervalFieldActionPerformed

	private void jbuttonSelectFiltActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbuttonSelectFiltActionPerformed

            filterChain.customize();
	}//GEN-LAST:event_jbuttonSelectFiltActionPerformed

	private void jaerFilterHelpMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jaerFilterHelpMIActionPerformed
            showInBrowser(JaerConstants.HELP_URL_JAER_HOME);
	}//GEN-LAST:event_jaerFilterHelpMIActionPerformed

    private void resetPerformanceMeasurementMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetPerformanceMeasurementMIActionPerformed
        if (filterChain != null) {
            filterChain.resetResetPerformanceMeasurementStatistics();
        }
    }//GEN-LAST:event_resetPerformanceMeasurementMIActionPerformed

    private void highlightTFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightTFActionPerformed
        String s = highlightTF.getText();
        if (s == null || s.isEmpty()) {
            if (s == null) {
                s = "";
            }
            FilterPanel p = getSelectedFilterPanel();
            if (p == null) {
                highlightFilters(s);
            } else {
                p.highlightProperties(s);
            }
        }
    }//GEN-LAST:event_highlightTFActionPerformed

    private FilterPanel getSelectedFilterPanel() {
        for (FilterPanel p : filterPanels) {
            if (p.isControlsVisible()) {
                return p;
            }
        }
        return null;
    }

    private void highlightTFFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_highlightTFFocusGained
        highlightTF.setText(null);
    }//GEN-LAST:event_highlightTFFocusGained

    private void resetAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAllButtonActionPerformed
        filterChain.reset();
    }//GEN-LAST:event_resetAllButtonActionPerformed

    private void overviewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overviewButtonActionPerformed
        for (FilterPanel f : filterPanels) {
            f.setControlsVisible(false); // hide controls for all filters, exposing chain
        }
    }//GEN-LAST:event_overviewButtonActionPerformed

    private void highlightMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightMIActionPerformed
        highlightTF.requestFocusInWindow();
    }//GEN-LAST:event_highlightMIActionPerformed

    private void highlightTFKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_highlightTFKeyPressed
        // incremental search
        String s = highlightTF.getText();
        if (s == null || s.isEmpty()) {
            return;
        }
        FilterPanel p = getSelectedFilterPanel();
        if (p == null) {
            highlightFilters(s);
        } else {
            p.highlightProperties(s);
        }        // TODO add your handling code here:
    }//GEN-LAST:event_highlightTFKeyPressed

    private void filterVisibleBiases(String string) {
        if ((string == null) || string.isEmpty()) {
            for (FilterPanel p : filterPanels) {
                p.setVisible(true);
            }
        } else {
            for (FilterPanel p : filterPanels) {
                String s = p.getFilter().getClass().getSimpleName().toUpperCase();
                string = string.toUpperCase();
                if (s.indexOf(string) != -1) {
                    p.setVisible(true);
                } else {
                    p.setVisible(false);
                }
            }
        }
        validate();
    }

    /**
     * handles property change events from AEViewer when playmode changes
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == "playmode") {
            setModeMenuEnabled();
        } else if (evt.getPropertyName().equals("processingmode")) {
            if (evt.getNewValue() == FilterChain.ProcessingMode.ACQUISITION) {
                acquisitionModeMenuItem.setSelected(true);
            } else if (evt.getNewValue() == FilterChain.ProcessingMode.RENDERING) {
                renderingModeMenuItem.setSelected(true);
            }
        }
    }

    public void clearFiltersPanel() {
        filterPanels.clear();
        filtersPanel.removeAll();
    }

    public void addToFiltersPanel(FilterPanel p) {
        filtersPanel.add(p);
        filterPanels.add((PanelType) p);
    }

    /**
     * Return the filter panel for the specified filter. This can be used when
     * adding custom controls
     */
    public FilterPanel getFilterPanelForFilter(EventFilter filt) {
        for (FilterPanel p : filterPanels) {
            if (p.getFilter() == filt) {
                return p;
            }
        }

        return null;
    }

    private void showInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            log.warning("No Desktop support, can't show help from " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            log.warning("Couldn't show " + url + "; caught " + ex);
        }
    }

    private ArrayList<EventFilter> highlightedFilters = new ArrayList();

    private void highlightFilters(String s) {

        for (EventFilter f : highlightedFilters) {
            final FilterPanel filterPanelForFilter = getFilterPanelForFilter(f);
            TitledBorder b = (TitledBorder) filterPanelForFilter.getBorder();
            b.setTitleColor(Color.black);
            filterPanelForFilter.repaint();
        }
        highlightedFilters.clear();
        if (s.isEmpty()) {
            return;
        }
        for (EventFilter f : filterChain) {
            if (f.getClass().getSimpleName().toLowerCase().contains(s.toLowerCase())) {
                TitledBorder b = (TitledBorder) getFilterPanelForFilter(f).getBorder();
                b.setTitleColor(Color.red);
                highlightedFilters.add(f);
                getFilterPanelForFilter(f).repaint();

            }
        }
    }

    class ResetPerformanceStatisticsAction extends AbstractAction {

        public ResetPerformanceStatisticsAction() {
            super("Reset stats.");
            putValue(SHORT_DESCRIPTION, "Resets the filter performance statistics on next processing cycle.");
            putValue(MNEMONIC_KEY, KeyEvent.VK_S);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (filterChain != null) {
                filterChain.resetResetPerformanceMeasurementStatistics();
            }
        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButtonMenuItem acquisitionModeMenuItem;
    private javax.swing.JMenuItem customizeMenuItem;
    private javax.swing.JToggleButton disableFilteringToggleButton;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    protected javax.swing.JPanel filtersPanel;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem highlightMI;
    private javax.swing.JTextField highlightTF;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JMenuItem jaerFilterHelpMI;
    private javax.swing.JButton jbuttonSelectFilt;
    private javax.swing.JMenuItem loadMenuItem;
    private javax.swing.JMenuBar mainMenuBar;
    private javax.swing.JCheckBoxMenuItem measurePerformanceCheckBoxMenuItem;
    private javax.swing.ButtonGroup modeButtonGroup;
    private javax.swing.JMenu modeMenu;
    private javax.swing.JButton overviewButton;
    private javax.swing.JRadioButtonMenuItem renderingModeMenuItem;
    private javax.swing.JButton resetAllButton;
    private javax.swing.JMenuItem resetPerformanceMeasurementMI;
    private javax.swing.JCheckBoxMenuItem restoreFilterEnabledStateCheckBoxMenuItem;
    private javax.swing.JMenuItem saveAsMenuItem;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JToolBar toolBar1;
    private javax.swing.JTextField updateIntervalField;
    private javax.swing.JLabel updateIntervalLabel;
    private javax.swing.JMenu viewMenu;
    // End of variables declaration//GEN-END:variables
}
