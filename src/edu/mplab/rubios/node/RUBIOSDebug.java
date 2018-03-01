package edu.mplab.rubios.node;
import java.io.File;
import java.io.FileWriter;

/**
 * RUBIOSDebug
 * <p>
 * A utility class to allow services to debug to a file
 *
 * @Authors: Javier R. Movellan 
 * @Copyright UCSD, Machine Perception Laboratory, and Javier R. Movellan
 * @License:  GPL
 * @Date: April 23, 2006
 *
 */
class RUBIOSDebug {
    public static final String debugFileEnvVar = "RUBIOSdebug_file";
    static RUBIOSDebug instance = null;
    FileWriter fp;

    public static void message(String s)
    {
	if (getInstance() != null) {
	    getInstance().sendMessage(s);
	}
    }

    private RUBIOSDebug(String debugFilePath)
    {
	File outputFile = new File(debugFilePath);

	try {
	    fp = new FileWriter(outputFile, true);
	}
	catch (Exception ex) {
	    ex.printStackTrace();
	}
    }

    public static RUBIOSDebug getInstance()
    {
	if (instance == null) {
	    String s = System.getenv(debugFileEnvVar);

	    if (s != null && s.length() != 0) {
		instance = new RUBIOSDebug(s);
	    } else {
		//System.err.println("no debug file specified in env var " +
		//		   debugFileEnvVar);
	    }
	}
	return instance;
    }
    private void sendMessage(String s)
    {
	try {
	    fp.write(s + "\n");
	    fp.flush();
	}
	catch (Exception ex) {
	    ex.printStackTrace();
	}
    }
}
