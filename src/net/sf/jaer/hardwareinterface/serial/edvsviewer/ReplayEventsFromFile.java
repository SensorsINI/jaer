package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

public class ReplayEventsFromFile extends Thread {

	ReplayEventsFromFile() {
System.out.println("here 0"); System.out.flush();
	}

	public void run() {
		File logFile;
		FileInputStream logFileInStream = null;
		InputStreamReader isr;
		LineNumberReader lnr;
		String line;

		long eventCounter = 0;

		int eventP, eventX, eventY;
		long eventTime, eventTimeOffset;

System.out.println("here 1"); System.out.flush();

		EventProcessorWindow ivTMP = new EventProcessorWindow(null, null);

System.out.println("here 2"); System.out.flush();

		JFileChooser fChooserTXT = new JFileChooser();

System.out.println("here 3"); System.out.flush();

		fChooserTXT.setMultiSelectionEnabled(false);
		fChooserTXT.setFileFilter(new FileEndingFilter(".TXT"));
		fChooserTXT.setFileSelectionMode(JFileChooser.FILES_ONLY);

System.out.println("here 4"); System.out.flush();
		Preferences prefs = Preferences.userNodeForPackage(ReplayEventsFromFile.class);
		fChooserTXT.setCurrentDirectory(new File(prefs.get("CurrentDirTXT", "C:/")));

System.out.println("here 5"); System.out.flush();
		int returnVal = fChooserTXT.showOpenDialog(ivTMP);
System.out.println("here 6"); System.out.flush();
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			String fileName = fChooserTXT.getSelectedFile().getAbsolutePath();

					// remember this directory if approved!
			prefs.put("CurrentDirTXT", (fChooserTXT.getCurrentDirectory()).toString());

			if (fileName.contains(".") == false) {
				fileName=fileName+".TXT";
			}
System.out.println("Filename: " + fileName);

			try {
				logFile = new File(fileName);
				if (logFile.exists()) {
					logFileInStream = new FileInputStream(logFile);
				}

				isr = new InputStreamReader(logFileInStream);
				lnr = new LineNumberReader(isr);

				eventTimeOffset = (System.nanoTime() / 1000);

				line = lnr.readLine();
				while (line != null) {

					eventP = 0;
					eventX = 0;
					eventY = 0;
					eventTime = 0;
					try {
						eventX = Integer.parseInt(line.substring( 0,  3).trim());
						eventY = Integer.parseInt(line.substring( 4,  7).trim());
						if (line.charAt(8) == '1') eventP = 1;
//						eventP = Integer.parseInt(line.substring( 7,  7).trim());
						eventTime = Integer.parseInt(line.substring(10).trim());
//String os = String.format("%3d %3d %1d %10d\n", eventX, eventY, eventP, eventTime);
//System.out.println(os);
					} catch (Exception e) { System.out.println("error parsing line " + line);
					}

					eventCounter++;
					//						System.out.println("Counter: " + eventCounter);

					while (((System.nanoTime()/1000) - eventTimeOffset) < eventTime) {
						yield();
					}

					//						System.out.printf("sending pixel %1d %3d %3d\n", eventP, eventX, eventY);

					ivTMP.processNewEvent(eventX, eventY, eventP);

					line = lnr.readLine();	// fetch next line
				}

				//logFileInStream.close();

				// done...
			} catch (Exception e) {
				System.out.println("Error processing events file!");
				System.out.println(e);
				e.printStackTrace();
			}
			try {
				logFileInStream.close();
			} catch (Exception e) {
			}

			if (ivTMP != null) ivTMP.close();

		}		
	}

	private class FileEndingFilter extends FileFilter {

		private String ending="";

		FileEndingFilter(String fileNameEnding) {
			ending = fileNameEnding;
		}
		public boolean accept(File f) {
			if (f.isDirectory()) {
				return true;
			}

			String fileName = f.getName();

			if ((fileName.toUpperCase()).endsWith(ending) ) {
				return(true);
			}
			return false;
		}

		//The description of this filter
		public String getDescription() {
			if (ending==null) {
				return("directories");
			}
			if (ending=="") {
				return("directories");
			}
			return(ending + "-files");
		}
	}

}
