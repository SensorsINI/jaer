/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;

import net.sf.jaer.Description;

/**
 * This class will scan for filters and print out the wiki page that indexes them.
 * 
 * When the file-selection box pops up, make a text-file to write to. You can
 * then copy this file into the wiki page at:
 * https://sourceforge.net/apps/trac/jaer/wiki/FilterIndex
 * to update the list of filters.
 * 
 * @author Peter
 */
public class FilterIndexPrinter {
	public static void main(final String[] args) throws IOException {
		final List<String> classList = SubclassFinder.findSubclassesOf("net.sf.jaer.eventprocessing.EventFilter2D");

		class Filter implements Comparable<Filter> {
			final String fullName;
			final String shortName;

			Filter(final String longName) {
				fullName = longName;
				final int point = longName.lastIndexOf(".");
				shortName = longName.substring(point + 1, longName.length());

			}

			@Override
			public int compareTo(final Filter o) {
				return shortName.compareTo(o.shortName);
			}
		}

		final List<Filter> filterList = new ArrayList<Filter>();
		for (final String c : classList) {
			filterList.add(new Filter(c));
		}

		Collections.sort(filterList);

		/** Get file to save into */
		final JFileChooser fileChooser = new JFileChooser(".");
		final int status = fileChooser.showOpenDialog(null);

		File selectedFile = null;
		if (status == JFileChooser.APPROVE_OPTION) {
			selectedFile = fileChooser.getSelectedFile();
			// System.out.println(selectedFile.getParent());
			// System.out.println(selectedFile.getName());
		}
		else if (status == JFileChooser.CANCEL_OPTION) {
			// System.out.println(JFileChooser.CANCEL_OPTION);
		}

		if (selectedFile == null) {
			return;
		}

		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(selectedFile);
		}
		catch (final FileNotFoundException ex) {
			Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
			return;
		}

		// Print the file.
		System.out.println("Printing file ...");
		final PrintStream ps = new PrintStream(fout);
		ps.println("# List of Event-Processing Filters");
		ps.println();
		ps.println("Click on a filter for more info.");
		ps.println();

		ps.println("|Filter Name|Description|Package|");
		ps.println("|-----------|-----------|-------|");

		for (final Filter f : filterList) {
			Description des = null;

			try {
				final Class<?> c = Class.forName(f.fullName);

				if (c.isAnnotationPresent(Description.class)) {
					des = c.getAnnotation(Description.class);
				}
			}
			catch (final SecurityException ex) {
				Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
			}
			catch (final ClassNotFoundException ex) {
				Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
			}

			String description;
			if (des == null) {
				description = " ";
			}
			else {
				description = des.value();
			}

			ps.println("|[" + f.shortName + "](filt." + f.fullName + ")|" + description + "|"
				+ f.fullName.substring(0, f.fullName.length() - f.shortName.length() - 1) + "|");
		}

		ps.println();
		ps.println("Run the class net.sf.jaer.util.FilterIndexPrinter to regenerate this list.");

		ps.close();
		fout.close();
		System.out.println("Done");
	}
}
