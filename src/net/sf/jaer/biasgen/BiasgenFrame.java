/*
 * Tmpdiff128IPotGUI.java
 *
 * Created on September 21, 2005, 12:00 PM
 */
package net.sf.jaer.biasgen;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.util.ExceptionListener;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.XMLFileFilter;

/**
 * A generic application frame for controlling a bias generator. You build the
 * bias generator, then construct this JFrame supplying the Biasgen as a
 * constructor argument. The BiasgenFrame then constructs itself to show the
 * Biasgen controls. The default construction delegates the job of populating
 * the main panel to BiasgenPanel.
 *
 * @author tobi
 */
public class BiasgenFrame extends javax.swing.JFrame implements UndoableEditListener {

    static Preferences prefs = Preferences.userNodeForPackage(BiasgenFrame.class);
    static Logger log = Logger.getLogger("Biasgen");
    private Biasgen biasgen;
    JPanel biasgenPanel = null;
    //    UndoableEditSupport editSupport=new UndoableEditSupport();
    UndoManager undoManager = new UndoManager();
    String HELP_URL = "http://www.ini.unizh.ch/~tobi/biasgen";
    RecentFiles recentFiles = null;
    File lastFile = null;
    File currentFile = null;
    private boolean fileModified = false;
    Chip chip;
    private boolean viewFunctionalBiasesEnabled = prefs.getBoolean("BiasgenFrame.viewFunctionalBiasesEnabled", false);
    private String defaultFolder = "";

    /**
     * Creates new form BiasgenApp, using an existing {@link Biasgen}.
     *
     * @param chip a chip with a biasgen
     */
    public BiasgenFrame(Chip chip) {
        if (chip.getBiasgen() == null) {
            throw new RuntimeException("null biasgen while constructing BiasgenFrame");
        }
        this.chip = chip;
        biasgen = chip.getBiasgen();
		//        try {
        //            UIManager.setLookAndFeel(
        //                    //new javax.swing.plaf.metal.MetalLookAndFeel()
        //                    UIManager.getSystemLookAndFeelClassName()
        //                    );
        //        } catch (Exception e) {
        //            e.printStackTrace();
        //        }
        initComponents();
        //        HardwareInterfaceException.addExceptionListener(this);
        fixUndoRedo();
        buildControlPanel(biasgen);
        setViewFunctionalBiasesEnabled(isViewFunctionalBiasesEnabled()); // adds it to the frame content panel - don't replace or we lose toolbar
        JMenu viewBiasOptionsMenu = PotGUIControl.viewMenu; // TODO assumes POTGUIControl is only type of control, not true anymore
        mainMenuBar.add(viewBiasOptionsMenu, 2);
        viewBiasOptionsMenu.addMenuListener(new MenuListener() {

            @Override
            public void menuDeselected(MenuEvent e) {
                SwingUtilities.invokeLater(new Thread() {

                    @Override
                    public void run() {
                        //                        System.out.println("repack frame");
                        pack();
                    }
                });
            }

            @Override
            public void menuSelected(MenuEvent e) {
                //                System.out.println("view menu selected");
            }

            @Override
            public void menuCanceled(MenuEvent e) {
                //                System.out.println("view menu canceled");
            }
        });
        String lastFilePath = prefs.get("BiasgenFrame.lastFile", defaultFolder);
        lastFile = new File(lastFilePath);
        recentFiles = new RecentFiles(prefs, fileMenu, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt) {
                File f = new File(evt.getActionCommand());
                if ((f != null) && f.isFile()) {
                    setStatusMessage("importing " + evt.getActionCommand());
                    try {
                        importPreferencesFromFile(f);
                    } catch (Exception fnf) {
                        log.warning(fnf.toString());
                        recentFiles.removeFile(f);
                    }
                } else if ((f != null) && f.isDirectory()) {
                    try {
                        importPreferencesDialog();
                    } catch (Exception dnf) {
                        log.warning(dnf.toString());
                        recentFiles.removeFile(f);
                    }
                }
            }
        });
        setTitle(chip.getName() + " - " + lastFile.getName() + " - Biases ");
        //        saveMenuItem.setEnabled(false); // until we load or save a file
        pack();

        defaultFolder = System.getProperty("user.dir");
        try {
            File f = new File(defaultFolder);
            File f2 = new File(f.getParent());
            File f3 = new File(f2.getParent());
            defaultFolder = f3 + File.separator + "biasgenSettings";
        } catch (Exception e) {
        }

    }

    /**
     * Checks if there are modifications, and if so, offers to save them.
     * Returns true if it is OK to exit, meaning either there are no
     * modifications, or there are modifications and they have been saved.
     * Returns false if user cancels save.
     *
     * @return true if OK to exit
     */
    public boolean isModificationsSaved() {
        if (!isFileModified()) {
            return true;
        }
        log.warning("unsaved biasgen setting changes");
        Object[] options = {"Save", "Discard", "Cancel"};
        int ret = JOptionPane.showOptionDialog(
                getContentPane(),
                "Save modified biasgen settings?",
                "Save bias modifications?",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);
        if (ret == JOptionPane.YES_OPTION) {
            return exportPreferencesDialog();
        } else if (ret == JOptionPane.CANCEL_OPTION) {
            return false;
        }
        return true;
    }

    void undo() {
        try {
            //            System.out.println("biasgenFrame undo");
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

    @Override
    public void undoableEditHappened(UndoableEditEvent undoableEditEvent) {
        //        System.out.println("BiasgenFrame undoableEditEvent");
        undoManager.addEdit(undoableEditEvent.getEdit());
        fixUndoRedo();
        String s = getTitle();
        if (s == null) {
            return;
        }
        if (s.lastIndexOf('*') == -1) {
            setTitle(getTitle() + "*");
        }
        setFileModified(true);
    }

    void fixUndoRedo() {
        undoEditMenuItem.setEnabled(undoManager.canUndo());
        redoEditMenuItem.setEnabled(undoManager.canRedo());
        undoButton.setEnabled(undoManager.canUndo());
        redoButton.setEnabled(undoManager.canRedo());

    }

    void resend() {
		// if biasgen doesn't have a hardware interface, we try to open one here in case one was not opened. we don't do
        // this in biasgen because we would get a storm of compleints on loading preferences, etc
        try {
            if (!biasgen.isOpen()) {
                biasgen.open();
            }
            biasgen.sendConfiguration(biasgen);
        } catch (HardwareInterfaceException e) {
            log.warning("BiasgenFrame.resend(): " + e.getMessage());
        }
    }

    void importPreferencesFromFile(File f) throws Exception {
        log.info("Current chip object is class " + chip == null ? null : chip.getClass() + "; importing biasgen settings from File " + f);
        InputStream is = new BufferedInputStream(new FileInputStream(f));
        biasgen.importPreferences(is);
        setCurrentFile(f);
        setFileModified(false);
        recentFiles.addFile(f);
    }

    private void exportPreferencesToFile(File f) throws Exception {
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
        biasgen.exportPreferences(os);
        setCurrentFile(f);
        setFileModified(false);
        log.info("exported biasgen preferences to file " + f);
    }

    void setCurrentFile(File f) {
        currentFile = new File(f.getPath());
        lastFile = currentFile;
        prefs.put("BiasgenFrame.lastFile", lastFile.toString());
        saveMenuItem.setEnabled(true);
        saveMenuItem.setText("Save " + currentFile.getName());
        setTitle(chip.getName() + " - " + lastFile.getName() + " - Biases ");
    }

    /**
     * Shows a dialog to choose a file to store preferences to. If the users
     * successfully writes the file, then the preferences are also stored in the
     * preferences tree as default values.
     *
     * @return true if preferences successfully saved, false if user cancels or
     * there is an exception saving the settings.
     *
     */
    public boolean exportPreferencesDialog() {
        boolean done = false;
        while (!done) {
            JFileChooser chooser = new JFileChooser();
            XMLFileFilter filter = new XMLFileFilter();
            chooser.setFileFilter(filter);
            chooser.setCurrentDirectory(lastFile);
            chooser.setApproveButtonText("Export bias settings");
            int retValue = chooser.showSaveDialog(this);
            if (retValue == JFileChooser.CANCEL_OPTION) {
                return false;
            } else if (retValue == JFileChooser.APPROVE_OPTION) {
                try {
                    lastFile = chooser.getSelectedFile();
                    if (!lastFile.getName().endsWith(XMLFileFilter.EXTENSION)) {
                        lastFile = new File(lastFile.getCanonicalPath() + XMLFileFilter.EXTENSION);
                    }
                    if (lastFile.exists()) {
                        int retVal = JOptionPane.showConfirmDialog(this, lastFile + " already exists, overwrite it?", "Overwrite file?", JOptionPane.OK_CANCEL_OPTION);
                        if (retVal == JOptionPane.CANCEL_OPTION) {
                            continue;
                        }
                    }
                    exportPreferencesToFile(lastFile);
                    done = true;
                    prefs.put("BiasgenFrame.lastFile", lastFile.toString());
                    recentFiles.addFile(lastFile);
                    return true;
                } catch (Exception fnf) {
                    setStatusMessage(fnf.getMessage());
                    log.warning(fnf.toString());
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Shows a file dialog from which to import preferences for biases from the
     * tree.
     */
    public void importPreferencesDialog() {
        JFileChooser chooser = new JFileChooser();
        XMLFileFilter filter = new XmlAedatFileFilter();
        
        String lastFilePath = prefs.get("BiasgenFrame.lastFile", defaultFolder);
        lastFile = new File(lastFilePath);
        chooser.setFileFilter(filter);
        chooser.setCurrentDirectory(lastFile);
        int retValue = chooser.showOpenDialog(this);
        if (retValue == JFileChooser.APPROVE_OPTION) {
            try {
                lastFile = chooser.getSelectedFile();
                importPreferencesFromFile(lastFile);
            } catch (Exception fnf) {
                log.warning(fnf.toString());
            }
        }
        //        resend(); // shouldn't be necessary with the batch edit start/end in biasgen.importPreferences
    }

  

    void setStatusMessage(String s) {
        statusTextField.setText(s);
    }

    private void setStatusColor(Color c) {
        statusTextField.setForeground(c);
    }

    private void startStatusClearer(Color color) {
        setStatusColor(color);
        if ((statusClearerThread != null) && statusClearerThread.isAlive()) {
            statusClearerThread.renew();
        } else {
            statusClearerThread = new StatusClearerThread();
            statusClearerThread.start();
        }

    }
    StatusClearerThread statusClearerThread = null;
    /**
     * length of exception highlighting in status bar in ms
     */
    public final long STATUS_DURATION = 1000;

    class StatusClearerThread extends Thread {

        long endTime;

        public void renew() {
            //            System.out.println("renewing status change");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
        }

        @Override
        public void run() {
            //            System.out.println("start status clearer thread");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
            try {
                while (System.currentTimeMillis() < endTime) {
                    Thread.currentThread();
                    Thread.sleep(STATUS_DURATION);
                }
                setStatusColor(Color.DARK_GRAY);
            } catch (InterruptedException e) {
            }
            ;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
	// <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		viewBiasesButtonGroup = new javax.swing.ButtonGroup();
		mainToolBar = new javax.swing.JToolBar();
		revertButton = new javax.swing.JButton();
		resendButton = new javax.swing.JButton();
		undoButton = new javax.swing.JButton();
		redoButton = new javax.swing.JButton();
		flashButton = new javax.swing.JButton();
		suspendToggleButton = new javax.swing.JToggleButton();
		statusTextField = new javax.swing.JTextField();
		mainMenuBar = new javax.swing.JMenuBar();
		fileMenu = new javax.swing.JMenu();
		saveMenuItem = new javax.swing.JMenuItem();
		exportPreferencesMenuItem = new javax.swing.JMenuItem();
		loadMenuItem = new javax.swing.JMenuItem();
		importPreferencesMenuItem = new javax.swing.JMenuItem();
		jSeparator1 = new javax.swing.JSeparator();
		exitMenuItem = new javax.swing.JMenuItem();
		editMenu = new javax.swing.JMenu();
		undoEditMenuItem = new javax.swing.JMenuItem();
		redoEditMenuItem = new javax.swing.JMenuItem();
		biasMenu = new javax.swing.JMenu();
		revertMenuItem = new javax.swing.JMenuItem();
		resendMenuItem = new javax.swing.JMenuItem();
		jSeparator3 = new javax.swing.JSeparator();
		flashMenuItem = new javax.swing.JMenuItem();
		jSeparator4 = new javax.swing.JSeparator();
		suspendCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();

		setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		setTitle("HW Config");
		setName("Biasgen"); // NOI18N

		revertButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/revert.GIF"))); // NOI18N
		revertButton.setToolTipText("Revert to last saved or loaded settings");
		revertButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
		revertButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				revertButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(revertButton);

		resendButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/resend.GIF"))); // NOI18N
		resendButton.setToolTipText("Resend bias values to chip");
		resendButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
		resendButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				resendButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(resendButton);

		undoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/undo.gif"))); // NOI18N
		undoButton.setToolTipText("Undo last bias change");
		undoButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
		undoButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				undoButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(undoButton);

		redoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/redo.gif"))); // NOI18N
		redoButton.setToolTipText("Redo bias change");
		redoButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
		redoButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				redoButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(redoButton);

		flashButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/flash.GIF"))); // NOI18N
		flashButton.setToolTipText("Write bias values to on-board flash memory");
		flashButton.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
		flashButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				flashButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(flashButton);

		suspendToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/net/sf/jaer/biasgen/suspend.gif"))); // NOI18N
		suspendToggleButton.setToolTipText("Toggles setting all bias currents to zero");
		suspendToggleButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				suspendToggleButtonActionPerformed(evt);
			}
		});
		mainToolBar.add(suspendToggleButton);

		getContentPane().add(mainToolBar, java.awt.BorderLayout.NORTH);

		statusTextField.setEditable(false);
		statusTextField.setToolTipText("HardwareIntefaceExceptions show here");
		statusTextField.setFocusable(false);
		getContentPane().add(statusTextField, java.awt.BorderLayout.SOUTH);

		fileMenu.setMnemonic('F');
		fileMenu.setText("File");

		saveMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
		saveMenuItem.setMnemonic('S');
		saveMenuItem.setText("Save settings");
		saveMenuItem.setToolTipText("Saves the current settings");
		saveMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				saveMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(saveMenuItem);

		exportPreferencesMenuItem.setText("Save settings as...");
		exportPreferencesMenuItem.setToolTipText("Exports settings to an XML file");
		exportPreferencesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				exportPreferencesMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(exportPreferencesMenuItem);

		loadMenuItem.setText("Revert settings");
		loadMenuItem.setToolTipText("Loads your saved default preferences");
		loadMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				loadMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(loadMenuItem);

		importPreferencesMenuItem.setText("Load settings...");
		importPreferencesMenuItem.setToolTipText("Loads settiings from a saved XML file");
		importPreferencesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				importPreferencesMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(importPreferencesMenuItem);

		fileMenu.add(jSeparator1);

		exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
		exitMenuItem.setMnemonic('X');
		exitMenuItem.setText("Exit (will kill JVM, e.g. matlab)");
		exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				exitMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(exitMenuItem);

		mainMenuBar.add(fileMenu);

		editMenu.setMnemonic('E');
		editMenu.setText("Edit");

		undoEditMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_MASK));
		undoEditMenuItem.setMnemonic('U');
		undoEditMenuItem.setText("Undo");
		undoEditMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				undoEditMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(undoEditMenuItem);

		redoEditMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_MASK));
		redoEditMenuItem.setMnemonic('R');
		redoEditMenuItem.setText("Redo");
		redoEditMenuItem.setEnabled(false);
		redoEditMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				redoEditMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(redoEditMenuItem);

		mainMenuBar.add(editMenu);

		biasMenu.setMnemonic('B');
		biasMenu.setText("Bias");

		revertMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
		revertMenuItem.setMnemonic('R');
		revertMenuItem.setText("Revert");
		revertMenuItem.setToolTipText("Revert to last loaded file");
		revertMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				revertButtonActionPerformed(evt);
			}
		});
		biasMenu.add(revertMenuItem);

		resendMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0));
		resendMenuItem.setMnemonic('e');
		resendMenuItem.setText("Resend");
		resendMenuItem.setToolTipText("Resend values");
		resendMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				resendButtonActionPerformed(evt);
			}
		});
		biasMenu.add(resendMenuItem);
		biasMenu.add(jSeparator3);

		flashMenuItem.setMnemonic('F');
		flashMenuItem.setText("Flash");
		flashMenuItem.setToolTipText("Flash current values to flash memory");
		flashMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				flashButtonActionPerformed(evt);
			}
		});
		biasMenu.add(flashMenuItem);
		biasMenu.add(jSeparator4);

		suspendCheckBoxMenuItem.setMnemonic('S');
		suspendCheckBoxMenuItem.setText("Suspend");
		suspendCheckBoxMenuItem.setToolTipText("Set all bias current to zero");
		suspendCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				suspendCheckBoxMenuItemActionPerformed(evt);
			}
		});
		biasMenu.add(suspendCheckBoxMenuItem);

		mainMenuBar.add(biasMenu);

		setJMenuBar(mainMenuBar);

		pack();
	}// </editor-fold>//GEN-END:initComponents
	JPanel functionalBiasgenPanel = null;

	private void suspendToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_suspendToggleButtonActionPerformed
            if (biasgen != null) {
                if (suspendToggleButton.isSelected()) {
                    biasgen.suspend();
                } else {
                    biasgen.resume();
                }
            }
            suspendCheckBoxMenuItem.setSelected(suspendToggleButton.isSelected());
	}//GEN-LAST:event_suspendToggleButtonActionPerformed

	private void suspendCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_suspendCheckBoxMenuItemActionPerformed
            if (biasgen != null) {
                if (suspendCheckBoxMenuItem.isSelected()) {
                    biasgen.suspend();
                } else {
                    biasgen.resume();
                }
            }
            suspendToggleButton.setSelected(suspendCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_suspendCheckBoxMenuItemActionPerformed

	private void flashButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flashButtonActionPerformed
            try {
                biasgen.flashConfiguration(biasgen);
            } catch (HardwareInterfaceException e) {
                log.warning("BiasgenFrame.flashButtonActionPerformed(): " + e);
                Toolkit.getDefaultToolkit().beep();
            }
	}//GEN-LAST:event_flashButtonActionPerformed

	private void redoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoButtonActionPerformed
            redo();
	}//GEN-LAST:event_redoButtonActionPerformed

	private void undoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoButtonActionPerformed
            undo();
	}//GEN-LAST:event_undoButtonActionPerformed

	private void redoEditMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoEditMenuItemActionPerformed
            redo();
	}//GEN-LAST:event_redoEditMenuItemActionPerformed

	private void undoEditMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoEditMenuItemActionPerformed
            undo();
	}//GEN-LAST:event_undoEditMenuItemActionPerformed

	private void exportPreferencesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportPreferencesMenuItemActionPerformed
            exportPreferencesDialog();
	}//GEN-LAST:event_exportPreferencesMenuItemActionPerformed

	private void importPreferencesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importPreferencesMenuItemActionPerformed
            importPreferencesDialog();
	}//GEN-LAST:event_importPreferencesMenuItemActionPerformed

	private void resendButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resendButtonActionPerformed
            resend();
	}//GEN-LAST:event_resendButtonActionPerformed

	private void revertButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_revertButtonActionPerformed
            //        log.info("reverting settings");
            biasgen.loadPreferences();
            biasgen.storePreferences(); // to generate PreferenceChange event so that listeners will update.
	}//GEN-LAST:event_revertButtonActionPerformed

	private void loadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadMenuItemActionPerformed
            biasgen.loadPreferences(); // TODO not quite the right behavior, should be revert action here.
	}//GEN-LAST:event_loadMenuItemActionPerformed

	private void saveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuItemActionPerformed
            if ((currentFile == null) || !currentFile.exists()) {
                exportPreferencesDialog();
            } else {
                try {
                    exportPreferencesToFile(currentFile);
                } catch (Exception e) {
                    log.warning("Couldn't save to " + currentFile);
                }
            }
	}//GEN-LAST:event_saveMenuItemActionPerformed

	private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
		//        int ret=showInternalOptionDialog(
            //                Component parent,
            //                Object message,
            //                String title,
            //                int optionType,
            //                int messageType,
            //                Icon icon,
            //                Object[] options,
            //                Object initialValue
            //                )
            Object[] options = {"Yes, really exit JVM", "Just close window", "Cancel"};
            int ret = JOptionPane.showOptionDialog(
                    getContentPane(),
                    "Are you sure you want to exit (this will kill JVM and thus matlab if running from within matlab)?",
                    "Exit biasgen?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[1]);
            if (ret == JOptionPane.YES_OPTION) {
                biasgen.close();
                System.exit(0);
            } else if (ret == JOptionPane.NO_OPTION) {
                biasgen.close();
                dispose();
            }
	}//GEN-LAST:event_exitMenuItemActionPerformed

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JMenu biasMenu;
	private javax.swing.JMenu editMenu;
	private javax.swing.JMenuItem exitMenuItem;
	private javax.swing.JMenuItem exportPreferencesMenuItem;
	private javax.swing.JMenu fileMenu;
	private javax.swing.JButton flashButton;
	private javax.swing.JMenuItem flashMenuItem;
	private javax.swing.JMenuItem importPreferencesMenuItem;
	private javax.swing.JSeparator jSeparator1;
	private javax.swing.JSeparator jSeparator3;
	private javax.swing.JSeparator jSeparator4;
	private javax.swing.JMenuItem loadMenuItem;
	private javax.swing.JMenuBar mainMenuBar;
	private javax.swing.JToolBar mainToolBar;
	private javax.swing.JButton redoButton;
	private javax.swing.JMenuItem redoEditMenuItem;
	private javax.swing.JButton resendButton;
	private javax.swing.JMenuItem resendMenuItem;
	private javax.swing.JButton revertButton;
	private javax.swing.JMenuItem revertMenuItem;
	private javax.swing.JMenuItem saveMenuItem;
	private javax.swing.JTextField statusTextField;
	private javax.swing.JCheckBoxMenuItem suspendCheckBoxMenuItem;
	private javax.swing.JToggleButton suspendToggleButton;
	private javax.swing.JButton undoButton;
	private javax.swing.JMenuItem undoEditMenuItem;
	private javax.swing.ButtonGroup viewBiasesButtonGroup;
	// End of variables declaration//GEN-END:variables

    public static void main(String[] a) {
        HardwareInterface hw = HardwareInterfaceFactory.instance().getFirstAvailableInterface();
        if (hw == null) {
            throw new RuntimeException("no hardware interface found");
        }
        net.sf.jaer.chip.AEChip chip = new ch.unizh.ini.jaer.chip.retina.Tmpdiff128(hw);
        BiasgenFrame frame = new BiasgenFrame(chip);
        frame.setVisible(true);
    }

    public Biasgen getBiasgen() {
        return biasgen;
    }

    /**
     * Builds the frame around the biasgen, by asking the Biasgen for its
     * control panel and adding it to the content pane.
     *
     * @param biasgen the biasgen
     */
    public void buildControlPanel(Biasgen biasgen) {
        this.biasgen = biasgen;
        biasgenPanel = biasgen.buildControlPanel();
        getContentPane().add(biasgenPanel, BorderLayout.CENTER);
    }

    public boolean isFileModified() {
        return fileModified;
    }

    public void setFileModified(boolean fileModified) {
        this.fileModified = fileModified;
    }

    public boolean isViewFunctionalBiasesEnabled() {
        return viewFunctionalBiasesEnabled;
    }

    public void setViewFunctionalBiasesEnabled(boolean viewFunctionalBiasesEnabled) {
        if (viewFunctionalBiasesEnabled) {
            if ((biasgen != null) && (biasgen instanceof ChipControlPanel)) {
                if (functionalBiasgenPanel == null) {
                    functionalBiasgenPanel = ((ChipControlPanel) biasgen).getControlPanel();
                }
                getContentPane().remove(biasgenPanel);
                getContentPane().add(functionalBiasgenPanel);
                pack();
            } else {
            }
        } else {
            if (functionalBiasgenPanel != null) {
                getContentPane().remove(functionalBiasgenPanel);
            }
            getContentPane().add(biasgenPanel);
            pack();
        }
        this.viewFunctionalBiasesEnabled = viewFunctionalBiasesEnabled;
        prefs.putBoolean("BiasgenFrame.viewFunctionalBiasesEnabled", viewFunctionalBiasesEnabled);
    }

    @Override
    public void dispose() {
        MenuListener[] listeners = PotGUIControl.viewMenu.getMenuListeners();
        for (MenuListener listener : listeners) {
            PotGUIControl.viewMenu.removeMenuListener(listener);
        }
        super.dispose();
    }

    private class XmlAedatFileFilter extends XMLFileFilter {

        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }

            String extension = getExtension(f);
            if (extension != null) {
                if (extension.equals("xml") || extension.equals("aedat")) {
                    return true;
                } else {
                    return false;
                }
            }
            return true;
        }
    }
}
