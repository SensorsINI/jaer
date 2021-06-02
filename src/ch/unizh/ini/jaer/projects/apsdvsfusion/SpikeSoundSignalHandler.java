/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.jaer.util.SpikeSound;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.ParameterBrowserPanel;

/**
 * @author Dennis
 *
 */
public class SpikeSoundSignalHandler extends ParameterContainer implements
		SignalHandler {

	private int minX = 0;
	private int minY = 0;
	
	private int maxX = 128;
	private int maxY = 128;
	
	private SpikeSound spikeSound = new SpikeSound();
	
	private FiringModelMap inputMap = null;
	
	private SpatioTemporalFusion stf;
	
	private LinkedList<Integer> spikeTimesRecordBuffer = new LinkedList<Integer>();
	private LinkedList<Integer> spikeTimesRecord = null;
	int recordedMinTime = Integer.MAX_VALUE;
	int recordedMaxTime = Integer.MIN_VALUE;
	
	boolean recordTrace = false;
	
	/**
	 * @param name
	 * @param prefs
	 */
	public SpikeSoundSignalHandler(SpatioTemporalFusion stf, String name, Preferences prefs) {
		super(name, prefs);
		this.stf = stf;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler#signalAt(int, int, int, double)
	 */
	@Override
	public void signalAt(int x, int y, int time, double value) {
		if (x >= minX && x < maxX && y >= minY && y < maxY) {
			spikeSound.play();
			if (recordTrace) {
				spikeTimesRecordBuffer.add(time);
			}
		}
		if (recordTrace) {
			if (time < recordedMinTime)
				recordedMinTime = time;
			if (time > recordedMaxTime)
				recordedMaxTime = time;
		}
	}
	
	
	public void do_Copy_trace() {
		if (spikeTimesRecord != null) {
			StringBuilder sb = new StringBuilder();
			sb.append(recordedMinTime+"\t0\n");
			for (int i : spikeTimesRecord) {
				sb.append(i+"\t0\n"+i+"\t100\n"+i+"\t0\n");
			}
			sb.append(recordedMaxTime+"\t0\n");
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Clipboard clipboard = toolkit.getSystemClipboard();
			StringSelection strSel = new StringSelection(sb.toString());
			clipboard.setContents(strSel, null);
		}
	}
	

	/**
	 * @return the recordTrace
	 */
	public boolean isRecordTrace() {
		return recordTrace;
	}

	/**
	 * @param recordTrace the recordTrace to set
	 */
	public void setRecordTrace(boolean recordTrace) {
		if (recordTrace != this.recordTrace) {
			this.recordTrace = recordTrace;
			getSupport().firePropertyChange("recordTrace", !recordTrace, recordTrace);
		}
		
	}

	/**
	 * @return the minX
	 */
	public int getMinX() {
		return minX;
	}

	/**
	 * @param minX the minX to set
	 */
	public void setMinX(int minX) {
		getSupport().firePropertyChange("minX", this.minX, minX);
		this.minX = minX;
	}

	/**
	 * @return the minY
	 */
	public int getMinY() {
		return minY;
	}

	/**
	 * @param minY the minY to set
	 */
	public void setMinY(int minY) {
		getSupport().firePropertyChange("minY", this.minY, minY);
		this.minY = minY;
	}

	/**
	 * @return the maxX
	 */
	public int getMaxX() {
		return maxX;
	}

	/**
	 * @param maxX the maxX to set
	 */
	public void setMaxX(int maxX) {
		getSupport().firePropertyChange("maxX", this.maxX, maxX);
		this.maxX = maxX;
	}

	/**
	 * @return the maxY
	 */
	public int getMaxY() {
		return maxY;
	}

	/**
	 * @param maxY the maxY to set
	 */
	public void setMaxY(int maxY) {
		getSupport().firePropertyChange("maxY", this.maxY, maxY);
		this.maxY = maxY;
	}

	
	
	public void setInputMap(FiringModelMap map) {
		if (map != this.inputMap) {
			if (this.inputMap != null) {
				inputMap.removeSignalHandler(this);
			}
			FiringModelMap before = this.inputMap;
			this.inputMap = map;
			if (this.inputMap != null) {
				this.inputMap.addSignalHandler(this);
				getPrefs().putInt("inputMapID", this.inputMap.getMapID());
			}
			else 
				getPrefs().putInt("inputMapID", -1);
			getSupport().firePropertyChange("inputMap", before, map);
		}
	}
	
	@SuppressWarnings("rawtypes")
	JComboBox myComboBox = null;
	Object currentSelection = null;
	
	@SuppressWarnings("rawtypes")
	protected JComponent createCustomControls() {
		myComboBox = new JComboBox();
		myComboBox.setFont(myComboBox.getFont().deriveFont(10f));
		
		ArrayList<FiringModelMap> contents;
		if (stf != null) {
			stf.getSupport().addPropertyChangeListener("firingModelMaps", this);
			contents = stf.getFiringModelMaps();
		}
		else 
			contents = new ArrayList<FiringModelMap>();
		updateComboBox(new ArrayList<FiringModelMap>(), contents);
		
		myComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Object newSelection = myComboBox.getSelectedItem();
				if (newSelection != currentSelection) {
					if (stf != null) {
						synchronized (stf.getFilteringLock()) {
							currentSelection = newSelection;
							if (currentSelection instanceof FiringModelMap)
								setInputMap((FiringModelMap)currentSelection);
							else 
								setInputMap(null);
						}

					}

				}
			}
		});
		
		
		getSupport().addPropertyChangeListener("inputMap",new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getNewValue() != evt.getOldValue()) {
					if (evt.getNewValue() != null)
						myComboBox.setSelectedItem(evt.getNewValue());
					else
						myComboBox.setSelectedIndex(0);
				}
}
		});
		JPanel customPanel = new JPanel();
        customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.X_AXIS));
        customPanel.setAlignmentX(ParameterBrowserPanel.ALIGNMENT);
        final JLabel jLabel = new JLabel("Input map:");
        jLabel.setFont(jLabel.getFont().deriveFont(10f));
		customPanel.add(jLabel);
        customPanel.add(myComboBox);
        return customPanel;
	}
	
	@SuppressWarnings("unchecked")
	protected void updateComboBox(ArrayList<FiringModelMap> oldContents, ArrayList<FiringModelMap> newContents) {
		Object selection = myComboBox.getSelectedItem();
		if (myComboBox.getItemCount() == 0) {
			selection = this.inputMap;
		}
		myComboBox.removeAllItems();
		if (newContents != null) {
			myComboBox.addItem("none");
			for (FiringModelMap map : newContents) {
				myComboBox.addItem(map);
				if (!oldContents.contains(map))
					map.getSupport().addPropertyChangeListener("name",this);
			}
			if (newContents.contains(selection))
				myComboBox.setSelectedItem(selection);
			else if (selection == null) 
				myComboBox.setSelectedIndex(0);
		}
		if (oldContents != null) {
			for (FiringModelMap map : oldContents) {
				if (!newContents.contains(map)) 
					map.getSupport().removePropertyChangeListener(this);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
		super.propertyChange(propertyChangeEvent);
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		if (propertyChangeEvent.getSource() == stf) {
			if (propertyChangeEvent.getPropertyName().equals("firingModelMaps")) {
				updateComboBox((ArrayList<FiringModelMap>)propertyChangeEvent.getOldValue(),(ArrayList<FiringModelMap>)propertyChangeEvent.getNewValue());
			}
		}
		else if (propertyChangeEvent.getSource() instanceof FiringModelMap) {
			ArrayList<FiringModelMap> contents;
			if (stf != null) contents = stf.getFiringModelMaps();
			else contents = new ArrayList<FiringModelMap>();
			updateComboBox(contents, contents);
		}
	}

	@Override
	public void restoreParameters() {
    	super.restoreParameters();
    	int inputMapID = getPrefs().getInt("inputMapID",-1);
    	if (inputMapID >= 0) {
    		ArrayList<FiringModelMap> maps = SpatioTemporalFusion.getInstance(this).getFiringModelMaps();
    		synchronized (maps) { 
    			for (FiringModelMap map : maps) {
    				if (map.getMapID() == inputMapID)
    					this.setInputMap(map);
    			}
    		}
    	}
    }
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.SignalHandler#reset()
	 */
	@Override
	public void reset() {
		if (recordTrace) {
			spikeTimesRecord = spikeTimesRecordBuffer;
			spikeTimesRecordBuffer = new LinkedList<Integer>();
		}
	}

}
