package ch.unizh.ini.jaer.projects.multitracking;



import java.io.File;
import java.util.ArrayList;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;



public class easyRecordInterface extends EventFilter2D{

	private File loggingFile;

	private boolean confirmFilename = true;

	private AEViewer aevi;

	private JAERViewer jaevi;

	private ArrayList<AEViewer> arrayOfAEvi=new ArrayList();

	private ArrayList<File> arrayOfRecordedFile;


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
		aevi = this.chip.getAeViewer();
		jaevi = aevi.getJaerViewer();
		arrayOfAEvi = jaevi.getViewers();
		System.out.println("list of active viewers");
		for (AEViewer aev : arrayOfAEvi){
			System.out.println(aev.getName());
		}
	}

	public void doStartEasyrecord(){
		/*		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run(){
				//On cr une nouvelle instance de notre JDialog
		 */		aevi = this.chip.getAeViewer();
		 jaevi = aevi.getJaerViewer();
		 arrayOfAEvi = jaevi.getViewers();
		 System.out.println("list of active viewers");
		 for (AEViewer aev : arrayOfAEvi){
			 System.out.println(aev.getName());
		 }
		 EasyRecordWindows fenetre = new EasyRecordWindows(this);
		 fenetre.setVisible(true);//On la rend visible
		 /*			}
		});*/

	}


	public void  sartRecording(String filename, String dataFileVersionNum) {
		//chip.getAeViewer().startLogging(filename,dataFileVersionNum);
		//jaevi.startSynchronizedLogging();
		//arrayOfRecordedFile=new ArrayList();
		for (AEViewer aev : arrayOfAEvi){
			System.out.println(aev.getName()+"startRecording");
			aev.startLogging(filename+aev.toString(), "2.0"); // (tobi) use a AE-DAT-2.0 file for now
			//arrayOfRecordedFile.add(loggingFile);


		}
	     jaevi.loggingEnabled = true;
		//return arrayOfRecordedFile;

	}

	public void stopLogging() {
		jaevi.stopSynchronizedLogging();
		//chip.getAeViewer().stopLogging(confirmFilename);
		/*for (AEViewer aev : arrayOfAEvi){
			System.out.println(aev.getName()+"stopRecording");
			aev.stopLogging(confirmFilename);
		}
*/
	}

	public boolean isConfirmFilename() {
		return confirmFilename;
	}
	public void setConfirmFilename(boolean confirmFilename) {
		this.confirmFilename = confirmFilename;
		putBoolean("confirmFilename", confirmFilename);
	}
}
