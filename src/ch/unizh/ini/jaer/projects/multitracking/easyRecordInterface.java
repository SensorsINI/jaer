package aTestSophie;



import java.io.File;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;



public class easyRecordInterface extends EventFilter2D{

	private File loggingFile;

    protected boolean confirmFilename = getBoolean("confirmFilename", true);

	public easyRecordInterface(AEChip chip) {
		super(chip);
		 // properties, tips and groups
		 final String size="Size", tim="Timing", disp="Display";
		setPropertyTooltip(disp,"showVectorsEnabled", "shows local orientation segments");
	}

	@Override
	public EventPacket filterPacket(EventPacket in) {
		return in;

	}



	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {
	}

	public void doStartEasyrecord(){
/*		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run(){
				//On cr une nouvelle instance de notre JDialog
*/				EasyRecordWindows fenetre = new EasyRecordWindows(this);
				fenetre.setVisible(true);//On la rend visible
/*			}
		});*/

	}


public File sartRecording(String filename, String dataFileVersionNum) {
//	chip.getAeViewer().startLogging(filename,dataFileVersionNum);
	return loggingFile;

	    }

public void stopLogging() {
	chip.getAeViewer().stopLogging(confirmFilename);

}

public boolean isConfirmFilename() {
    return confirmFilename;
}
public void setConfirmFilename(boolean confirmFilename) {
    this.confirmFilename = confirmFilename;
    putBoolean("confirmFilename", confirmFilename);
}
}
