/*
 * FilterFrame.java
 *
 * Created on October 31, 2005, 8:29 PM
 */
package net.sf.jaer.eventprocessing;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoableEditSupport;
import net.sf.jaer.JaerConstants;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.filter.PreferencesMover;
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
    static Preferences prefs;
    Logger log = Logger.getLogger("net.sf.jaer");
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

    private static EventFilter.CopiedProps copiedProps = null; // static so we can copy between chips which get a new FilterPanel

    UndoManager undoManager = new UndoManager();
    // undo/redo
    UndoableEditSupport editSupport = new UndoableEditSupport();
    UndoAction undoAction = new UndoAction();
    RedoAction redoAction = new RedoAction();

    private HideDisabledAction hideDiabledAction;

    protected HashMap<EventFilter, FilterPanel> filter2FilterPanelMap = new HashMap();

    /**
     * Creates new form FilterFrame
     */
    public FilterFrame(AEChip chip) {
        this.chip = chip;
        prefs = chip.getPrefs();
        this.filterChain = chip.getFilterChain();
        chip.setFilterFrame(this);
        setName("FilterFrame");
        initComponents();
        hideDiabledAction = new HideDisabledAction();
        hideDisnabledCB.setAction(hideDiabledAction);
        simpleCB.setSelected(prefs.getBoolean("simpleMode", false));
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
                        importPrefs(f);
                    } else if ((f != null) && f.isDirectory()) {
                        prefs.put("FilterFrame.lastFile", f.getCanonicalPath());
                        importPreferncesMIActionPerformed(null);
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

        editSupport.addUndoableEditListener(new MyUndoableEditListener());
        undoManager.discardAllEdits();
        fixUndoRedo();
        undoButton.setHideActionText(true);
        redoButton.setHideActionText(true);
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        toolTipManager.setInitialDelay(100); // Set initial delay to 500 milliseconds
        toolTipManager.setDismissDelay(2000); // Set dismiss delay to 2000 milliseconds
        pack();
    }

    /**
     * Should be called after setVisiible(true). Calls the optional initGUI in
     * each filter.
     */
    public void initGUI() {
        // now call optional initGUI for each filter
        for (EventFilter f : filterChain) {
            f.initGUI();
        }
    }

    protected class MyUndoableEditListener
            implements UndoableEditListener {

        public void undoableEditHappened(UndoableEditEvent e) {
            //Remember the edit and update the menus
            log.fine("adding undoable edit event" + e);
            undoManager.addEdit(e.getEdit());
            fixUndoRedo();
        }
    }

    private class UndoAction extends AbstractAction {

        public UndoAction() {
            putValue(NAME, "Undo");
            putValue(SHORT_DESCRIPTION, "Undo the last property change");
            putValue(SMALL_ICON, new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/undo.gif")));
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            undo();
            putValue(SHORT_DESCRIPTION, undoManager.getUndoPresentationName());
        }

    }

    private class OverviewAction extends AbstractAction {

        public OverviewAction() {
            putValue(NAME, "Overview");
            putValue(SHORT_DESCRIPTION, "Show overview of all filters in chip's FilterChain");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (FilterPanel f : filterPanels) {
                f.setControlsVisible(false); // hide controls for all filters, exposing chain
            }
        }
    }

    public boolean isHideDisabled() {
        return hideDiabledAction.isHideDisabled();
    }

    public void setHideDisabled(boolean hideDisabled) {
        hideDiabledAction.setHideDisabled(hideDisabled);
    }

    private class HideDisabledAction extends AbstractAction {

        private boolean hideDisabled = prefs.getBoolean("hideDisabled", false);

        public HideDisabledAction() {
            putValue(NAME, "Hide disabled");
            putValue(SHORT_DESCRIPTION, "Hides filters that are not enabled (by checkbox)");
//            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK));
            putValue(SELECTED_KEY, hideDisabled);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setHideDisabled(!isHideDisabled());
            putValue(SELECTED_KEY, isHideDisabled());
        }

        private void setVisibleIfEnabledOrNotHideDisabled(FilterPanel f) {
            f.setVisible(!isHideDisabled() || f.getFilter().isFilterEnabled());
            
        }

        /**
         * @return the hideDisabled
         */
        public boolean isHideDisabled() {
            return hideDisabled;
        }

        /**
         * @param hideDisabled the hideDisabled to set
         */
        public void setHideDisabled(boolean hideDisabled) {
            boolean oldHideDisabled = this.hideDisabled;
            this.hideDisabled = hideDisabled;
            if (oldHideDisabled != this.hideDisabled) {
                for (FilterPanel f : filterPanels) {
                    setVisibleIfEnabledOrNotHideDisabled(f);
                }
            }
            propertyChangeSupport.firePropertyChange(PROP_HIDEDISABLED, oldHideDisabled, hideDisabled);
            prefs.putBoolean("hideDisabled", isHideDisabled());
        }
        private final transient PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);
        public static final String PROP_HIDEDISABLED = "hideDisabled";
    }

    private class RedoAction extends AbstractAction {

        public RedoAction() {
            putValue(NAME, "Redo");
            putValue(SHORT_DESCRIPTION, "Redo the last property change");
            putValue(SMALL_ICON, new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/redo.gif")));
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            redo();
            putValue(SHORT_DESCRIPTION, undoManager.getRedoPresentationName());
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
        hideHighlightBG = new javax.swing.ButtonGroup();
        toolBar1 = new javax.swing.JToolBar();
        overviewButton = new javax.swing.JButton();
        disableFilteringToggleButton = new javax.swing.JToggleButton();
        resetAllButton = new javax.swing.JButton();
        updateIntervalPanel = new javax.swing.JPanel();
        updateIntervalLabel = new javax.swing.JLabel();
        updateIntervalField = new javax.swing.JTextField();
        selectFiltersJB = new javax.swing.JButton();
        tipLabel = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        undoButton = new javax.swing.JButton();
        redoButton = new javax.swing.JButton();
        filterJPanel = new javax.swing.JPanel();
        clearFilterJB = new javax.swing.JButton();
        highlightTF = new javax.swing.JTextField();
        simpleCB = new javax.swing.JCheckBox();
        hideDisnabledCB = new javax.swing.JCheckBox();
        scrollPane = new javax.swing.JScrollPane();
        filtersPanel = new javax.swing.JPanel();
        mainMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        importPreferncesMI = new javax.swing.JMenuItem();
        exportPreferencesMI = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        undoEditMenuItem = new javax.swing.JMenuItem();
        redoEditMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        customizeMenuItem = new javax.swing.JMenuItem();
        highlightMI = new javax.swing.JMenuItem();
        rebuildPanelB = new javax.swing.JMenuItem();
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
        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS));

        toolBar1.setAlignmentX(0.0F);

        overviewButton.setAction(new OverviewAction());
        overviewButton.setText("Overview");
        overviewButton.setToolTipText("Toggles overview of all filters in the FilterChain");
        overviewButton.setFocusable(false);
        overviewButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        overviewButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toolBar1.add(overviewButton);

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

        updateIntervalPanel.setLayout(new javax.swing.BoxLayout(updateIntervalPanel, javax.swing.BoxLayout.LINE_AXIS));

        updateIntervalLabel.setText("Update interval (ms)");
        updateIntervalPanel.add(updateIntervalLabel);

        updateIntervalField.setColumns(8);
        updateIntervalField.setToolTipText("Sets the maximum update interval for filters that notify observers, e.g. RectangularClusterTracker");
        updateIntervalField.setMaximumSize(new java.awt.Dimension(50, 30));
        updateIntervalField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateIntervalFieldActionPerformed(evt);
            }
        });
        updateIntervalPanel.add(updateIntervalField);

        toolBar1.add(updateIntervalPanel);

        selectFiltersJB.setText("Select Filters...");
        selectFiltersJB.setToolTipText("Opens dialog to select loaded filters");
        selectFiltersJB.setFocusable(false);
        selectFiltersJB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        selectFiltersJB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        selectFiltersJB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectFiltersJBActionPerformed(evt);
            }
        });
        toolBar1.add(selectFiltersJB);

        getContentPane().add(toolBar1);

        tipLabel.setText("<html>Enabled filters are processed from top to bottom");
        getContentPane().add(tipLabel);

        jPanel1.setAlignmentX(0.0F);
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.X_AXIS));

        undoButton.setAction(undoAction);
        undoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/undo.gif"))); // NOI18N
        undoButton.setToolTipText("Undo last property change");
        undoButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel1.add(undoButton);

        redoButton.setAction(redoAction);
        redoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/redo.gif"))); // NOI18N
        redoButton.setToolTipText("Redo last property change");
        redoButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel1.add(redoButton);

        filterJPanel.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        filterJPanel.setLayout(new javax.swing.BoxLayout(filterJPanel, javax.swing.BoxLayout.LINE_AXIS));

        clearFilterJB.setText("x");
        clearFilterJB.setToolTipText("Clear the highlights");
        clearFilterJB.setAlignmentX(0.5F);
        clearFilterJB.setIconTextGap(1);
        clearFilterJB.setMargin(new java.awt.Insets(1, 1, 1, 1));
        clearFilterJB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearFilterJBActionPerformed(evt);
            }
        });
        filterJPanel.add(clearFilterJB);

        highlightTF.setToolTipText("Filter properties");
        highlightTF.setMaximumSize(new java.awt.Dimension(100, 30));
        highlightTF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightTFActionPerformed(evt);
            }
        });
        highlightTF.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                highlightTFKeyReleased(evt);
            }
        });
        filterJPanel.add(highlightTF);

        simpleCB.setText("Simple");
        simpleCB.setToolTipText("Only show Preferred properties (commonly used)");
        simpleCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleCBActionPerformed(evt);
            }
        });
        filterJPanel.add(simpleCB);

        hideDisnabledCB.setText("Hide disabled");
        hideDisnabledCB.setToolTipText("");
        filterJPanel.add(hideDisnabledCB);

        jPanel1.add(filterJPanel);

        getContentPane().add(jPanel1);

        scrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setAlignmentX(0.0F);
        scrollPane.setPreferredSize(new java.awt.Dimension(300, 600));

        filtersPanel.setLayout(new javax.swing.BoxLayout(filtersPanel, javax.swing.BoxLayout.Y_AXIS));
        scrollPane.setViewportView(filtersPanel);

        getContentPane().add(scrollPane);

        mainMenuBar.setAlignmentX(0.0F);

        fileMenu.setMnemonic('f');
        fileMenu.setText("File");

        importPreferncesMI.setMnemonic('l');
        importPreferncesMI.setText("Import preferences...");
        importPreferncesMI.setToolTipText("Imports preferences for this entire filter chain attached to this AEChip");
        importPreferncesMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importPreferncesMIActionPerformed(evt);
            }
        });
        fileMenu.add(importPreferncesMI);

        exportPreferencesMI.setText("Export preferences...");
        exportPreferencesMI.setToolTipText("Exports preferences for this entire  filter chain attached to this AEChip");
        exportPreferencesMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportPreferencesMIActionPerformed(evt);
            }
        });
        fileMenu.add(exportPreferencesMI);
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

        editMenu.setMnemonic('E');
        editMenu.setText("Edit");

        undoEditMenuItem.setAction(undoAction);
        undoEditMenuItem.setMnemonic('U');
        undoEditMenuItem.setText("Undo");
        undoEditMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undoEditMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(undoEditMenuItem);

        redoEditMenuItem.setAction(redoAction);
        redoEditMenuItem.setMnemonic('R');
        redoEditMenuItem.setText("Redo");
        redoEditMenuItem.setEnabled(false);
        redoEditMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redoEditMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(redoEditMenuItem);

        mainMenuBar.add(editMenu);

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

        highlightMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        highlightMI.setMnemonic('h');
        highlightMI.setText("Filter");
        highlightMI.setToolTipText("Focuses Filter field to filter properties by string");
        highlightMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightMIActionPerformed(evt);
            }
        });
        viewMenu.add(highlightMI);

        rebuildPanelB.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        rebuildPanelB.setText("Rebuild panel");
        rebuildPanelB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuildPanelBActionPerformed(evt);
            }
        });
        viewMenu.add(rebuildPanelB);

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

        measurePerformanceCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        measurePerformanceCheckBoxMenuItem.setMnemonic('p');
        measurePerformanceCheckBoxMenuItem.setText("Measure filter processing time");
        measurePerformanceCheckBoxMenuItem.setToolTipText("Enables instrumentation of filter performance. Filter processing time statistics are printed to System.out. They appear in netbeans IDE console, for example, but not in built-in jAER console, which does not show System.out.");
        measurePerformanceCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                measurePerformanceCheckBoxMenuItemActionPerformed(evt);
            }
        });
        modeMenu.add(measurePerformanceCheckBoxMenuItem);

        resetPerformanceMeasurementMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));
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
        filterChain.initFilters(); // we need to run initFilter here because we have not gone through the whole AEChip construction process
        rebuildContents();

    }
    // list of individual filter panels
    protected ArrayList<PanelType> filterPanels = new ArrayList();

    public void rebuildPanel(FilterPanel oldPanel) {
        int idx = 0;
        for (FilterPanel fp : filterPanels) {
            if (oldPanel == fp) {
                FilterPanel newPanel = new FilterPanel(oldPanel.getFilter(), this);
                filtersPanel.remove(oldPanel);
                filtersPanel.add(newPanel, idx);
                filtersPanel.invalidate();
                break;
            }
            idx++;
        }
        pack();
    }

    /**
     * rebuilds the frame contents using the existing filters in the filterChain
     */
    final public void rebuildContents() {
        filterPanels.clear();
        filtersPanel.removeAll();
        filter2FilterPanelMap.clear();
        int n = 0;
        int w = 100, h = 30;
        for (EventFilter2D f : filterChain) {
            FilterPanel p = new FilterPanel(f, this);
            filtersPanel.add(p);
            filterPanels.add((PanelType) p);
            n++;
            h += p.getHeight();
            w = p.getWidth();
        }
        pack();
    }
    File lastFile;

	private void importPreferncesMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importPreferncesMIActionPerformed
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
                importPrefs(f);
                for (EventFilter filter : chip.getFilterChain()) {
                    PreferencesMover.OldPrefsCheckResult result = PreferencesMover.hasOldChipFilterPreferences(filter);
                    if (result.hasOldPrefs()) {
                        log.warning(result.message());
                        PreferencesMover.migratePreferencesDialog(this, chip, false, true, result.message());
                    } else {
                        log.fine(result.message());
                    }
                }
            }
	}//GEN-LAST:event_importPreferncesMIActionPerformed

    public void importPrefs(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            Preferences.importPreferences(fis);  // we import the tree into *this* preference node, which is not the one exported (which is root node)
            prefs.put("FilterFrame.lastFile", f.getCanonicalPath());
            log.info("imported preferences from " + f.toPath().toString());

            recentFiles.addFile(f);
            renewContents();
            JOptionPane.showMessageDialog(rootPane, String.format("<html>Loaded Preferences from <br>\t%s<br>and reconstructed the entire FilterChain", f.toPath()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRestoreFilterEnabledStateEnabled() {
        return restoreFilterEnabledStateEnabled;
    }

    final public void setRestoreFilterEnabledStateEnabled(boolean restoreFilterEnabledStateEnabled) {
        this.restoreFilterEnabledStateEnabled = restoreFilterEnabledStateEnabled;
        prefs.putBoolean("FilterFrame.restoreFilterEnabledStateEnabled", restoreFilterEnabledStateEnabled);
        restoreFilterEnabledStateCheckBoxMenuItem.setSelected(restoreFilterEnabledStateEnabled);
    }

	private void exportPreferencesMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportPreferencesMIActionPerformed
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
                    FileOutputStream fos = new FileOutputStream(file);
                    chip.getPrefs().exportSubtree(fos);
                    log.info("exported prefs subtree " + chip.getPrefs().absolutePath() + " to file " + file);
                    fos.close();
                    recentFiles.addFile(file);
                    prefs.put("FilterFrame.lastFile", file.getCanonicalPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
	}//GEN-LAST:event_exportPreferencesMIActionPerformed

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

	private void selectFiltersJBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectFiltersJBActionPerformed

            filterChain.customize();
	}//GEN-LAST:event_selectFiltersJBActionPerformed

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
        highlightOrShowOnly(s);
    }//GEN-LAST:event_highlightTFActionPerformed

    private FilterPanel getSelectedFilterPanel() {
        for (FilterPanel p : filterPanels) {
            if (p.isControlsVisible()) {
                return p;
            }
        }
        return null;
    }

    private void resetAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAllButtonActionPerformed
        filterChain.reset();
    }//GEN-LAST:event_resetAllButtonActionPerformed

    private void highlightMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightMIActionPerformed
        highlightTF.requestFocusInWindow();
    }//GEN-LAST:event_highlightMIActionPerformed

    private void clearFilterJBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearFilterJBActionPerformed
        highlightTF.setText("");
        highlightOrShowOnly("");
        highlightTF.requestFocus();
    }//GEN-LAST:event_clearFilterJBActionPerformed

    private void simpleCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleCBActionPerformed
        prefs.putBoolean("simpleMode", simpleCB.isSelected());
        updateHighlightedAndSimpleVisibilites();
    }//GEN-LAST:event_simpleCBActionPerformed

    /**
     * Updates visibility of controls
     */
    public void updateHighlightedAndSimpleVisibilites() {
        for (FilterPanel p : filterPanels) {
            if (p.isControlsVisible()) {
                p.showPropertyHighlightsOrVisibility(highlightTF.getText(), simpleCB.isSelected());
            }
        }
    }

    private void undoEditMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoEditMenuItemActionPerformed
        undo();
    }//GEN-LAST:event_undoEditMenuItemActionPerformed

    private void redoEditMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoEditMenuItemActionPerformed
        redo();
    }//GEN-LAST:event_redoEditMenuItemActionPerformed

    private void rebuildPanelBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuildPanelBActionPerformed
        rebuildContents();
    }//GEN-LAST:event_rebuildPanelBActionPerformed

    private void highlightTFKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_highlightTFKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
            highlightTF.setText("");
            highlightOrShowOnly(null);
        } else {
            String s = highlightTF.getText();
            highlightOrShowOnly(s);
        }
    }//GEN-LAST:event_highlightTFKeyReleased

    final void fixUndoRedo() {
        final boolean canUndo = undoManager.canUndo(), canRedo = undoManager.canRedo();
        undoAction.setEnabled(canUndo);
        redoAction.setEnabled(canRedo);
        if (canUndo) {
            undoAction.putValue(AbstractAction.SHORT_DESCRIPTION, undoManager.getUndoPresentationName());
        }
        if (canRedo) {
            redoAction.putValue(AbstractAction.SHORT_DESCRIPTION, undoManager.getRedoPresentationName());
        }
    }

    void undo() {
        try {
            undoManager.undo();
        } catch (CannotUndoException e) {
            Toolkit.getDefaultToolkit().beep();
            log.warning(e.getMessage());
        } finally {
            fixUndoRedo();
        }
    }

    void redo() {
        try {
            undoManager.redo();
        } catch (CannotRedoException e) {
            Toolkit.getDefaultToolkit().beep();
            log.warning(e.getMessage());
        } finally {
            fixUndoRedo();
        }
    }

    void addEdit(UndoableEdit edit) {
        undoManager.addEdit(edit);
//        fixUndoRedo();
//        String s = getTitle();
//        if (s == null) {
//            return;
//        }
//        if (s.lastIndexOf('*') == -1) {
//            setTitle(getTitle() + "*");
//        }
//        setFileModified(true);
    }

    private void highlightOrShowOnly(String searchString) {
        if (searchString == null) {
            searchString = "";
        }
        FilterPanel p = getSelectedFilterPanel();
        if (p == null) {
            highlightFilters(searchString);
        } else {
            p.showPropertyHighlightsOrVisibility(searchString, simpleCB.isSelected());
        }
    }

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
     * adding custom controls. The panel is returned even if it is within an
     * enclosed FilterChain
     *
     * @param filt the filter to look for panel for
     * @return the panel, or null
     */
    public FilterPanel getFilterPanelForFilter(EventFilter filt) {
        return filter2FilterPanelMap.get(filt);
//        for (FilterPanel p : filterPanels) {
//            if (p.getFilter() == filt) {
//                return p;
//            } // if the panel's filter has chain, then check if filt is one of these filters
//            else if (p.getFilter().getEnclosedFilterChain() != null) {
//                FilterChain c = p.getFilter().getEnclosedFilterChain();
//                for (EventFilter enclFilt : c) {
//                    if (enclFilt == filt) { // we found the enclosed filter, now we need the panel for it
//                        return p.getEnclosedFilterPanel(enclFilt);
//                    }
//                }
//            }
//        }
//
//        return null;
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

//        for (EventFilter f : highlightedFilters) {
//            final FilterPanel filterPanelForFilter = getFilterPanelForFilter(f);
//            TitledBorder b = (TitledBorder) filterPanelForFilter.getBorder();
//            b.setTitleColor(Color.black);
//            filterPanelForFilter.repaint();
//        }
        highlightedFilters.clear();
        for (EventFilter f : filterChain) {
            TitledBorder b = (TitledBorder) getFilterPanelForFilter(f).getBorder();
            if (s == null || s.isEmpty()) {
                b.setTitleColor(Color.black);
            } else if (f.getClass().getSimpleName().toLowerCase().contains(s.toLowerCase())) {
                b.setTitleColor(Color.red);
                highlightedFilters.add(f);
            } else {
                b.setTitleColor(Color.black);
            }
            getFilterPanelForFilter(f).repaint();
        }
        repaint();
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
    private javax.swing.JButton clearFilterJB;
    private javax.swing.JMenuItem customizeMenuItem;
    private javax.swing.JToggleButton disableFilteringToggleButton;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenuItem exportPreferencesMI;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JPanel filterJPanel;
    protected javax.swing.JPanel filtersPanel;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JCheckBox hideDisnabledCB;
    private javax.swing.ButtonGroup hideHighlightBG;
    private javax.swing.JMenuItem highlightMI;
    private javax.swing.JTextField highlightTF;
    private javax.swing.JMenuItem importPreferncesMI;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JMenuItem jaerFilterHelpMI;
    private javax.swing.JMenuBar mainMenuBar;
    private javax.swing.JCheckBoxMenuItem measurePerformanceCheckBoxMenuItem;
    private javax.swing.ButtonGroup modeButtonGroup;
    private javax.swing.JMenu modeMenu;
    private javax.swing.JButton overviewButton;
    private javax.swing.JMenuItem rebuildPanelB;
    private javax.swing.JButton redoButton;
    private javax.swing.JMenuItem redoEditMenuItem;
    private javax.swing.JRadioButtonMenuItem renderingModeMenuItem;
    private javax.swing.JButton resetAllButton;
    private javax.swing.JMenuItem resetPerformanceMeasurementMI;
    private javax.swing.JCheckBoxMenuItem restoreFilterEnabledStateCheckBoxMenuItem;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JButton selectFiltersJB;
    private javax.swing.JCheckBox simpleCB;
    private javax.swing.JLabel tipLabel;
    private javax.swing.JToolBar toolBar1;
    private javax.swing.JButton undoButton;
    private javax.swing.JMenuItem undoEditMenuItem;
    private javax.swing.JTextField updateIntervalField;
    private javax.swing.JLabel updateIntervalLabel;
    private javax.swing.JPanel updateIntervalPanel;
    private javax.swing.JMenu viewMenu;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the copiedProps
     */
    public static EventFilter.CopiedProps getCopiedProps() {
        return copiedProps;
    }

    /**
     * @param copiedProps the copiedProps to set
     */
    public static void setCopiedProps(EventFilter.CopiedProps copiedProps) {
        FilterFrame.copiedProps = copiedProps;
    }
}
