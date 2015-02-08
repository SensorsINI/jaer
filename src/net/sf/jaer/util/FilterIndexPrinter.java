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
import java.util.HashSet;
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
 * @author Peter OConnor, Tobi Delbruck
 */
public class FilterIndexPrinter {
	private static final int DIVIDE_INTO_NUM_FILES = 1; // if pages are too long, sf.net allura wiki barfs
	private static final int MAX_DESC_LENGTH = 1000;
        private static Logger log=Logger.getLogger("FilterIndexPrinter");
                
	public static void main(final String[] args) throws IOException {
		final List<String> classList = SubclassFinder.findSubclassesOf("net.sf.jaer.eventprocessing.EventFilter2D");

		// Remove duplicates.
		final HashSet<String> h = new HashSet<String>(classList);
		classList.clear();
		classList.addAll(h);

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
				return shortName.compareToIgnoreCase(o.shortName);
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
		final FileOutputStream foutAlpha[] = new FileOutputStream[FilterIndexPrinter.DIVIDE_INTO_NUM_FILES];

		try {
			fout = new FileOutputStream(selectedFile.getAbsolutePath());

			for (int i = 0; i < foutAlpha.length; i++) {
				foutAlpha[i] = new FileOutputStream(selectedFile.getAbsolutePath() + "_" + (i + 1));
			}
		}
		catch (final FileNotFoundException ex) {
			Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
			return;
		}

		// Print the file.
		log.info("Printing index to file "+selectedFile.getAbsolutePath());

		final PrintStream ps = new PrintStream(fout);

		final PrintStream psAlpha[] = new PrintStream[FilterIndexPrinter.DIVIDE_INTO_NUM_FILES];
		for (int i = 0; i < foutAlpha.length; i++) {
			psAlpha[i] = new PrintStream(foutAlpha[i]);
		}

		ps.println("# List of Event-Processing Filters");
		ps.println();
		ps.println("Click on a sub-list for more info on the contained filters.");
		ps.println("The various lists are sorted alphabetically by filter name.");
		ps.println();

		final int printPerFile = filterList.size() / FilterIndexPrinter.DIVIDE_INTO_NUM_FILES;
		PrintStream psCurrentFile = null;
		String firstInList = null;
		String lastInList = null;

		for (int i = 0, j = 0; i < filterList.size(); i++) {
			final Filter f = filterList.get(i);

			if (((i % printPerFile) == 0) && (j < (FilterIndexPrinter.DIVIDE_INTO_NUM_FILES))) {
				// Switch to next file.
				psCurrentFile = psAlpha[j++];

				// Add new list link to main index.
				firstInList = f.shortName;
				if (j == FilterIndexPrinter.DIVIDE_INTO_NUM_FILES) {
					lastInList = filterList.get(filterList.size() - 1).shortName;
				}
				else {
					lastInList = filterList.get((i + printPerFile) - 1).shortName;
				}

				ps.println("[List of Filters from " + firstInList + " to " + lastInList + "](" + "FilterIndex_" + j
					+ ")");

				// Print header.
				psCurrentFile.println("# List of Event-Processing Filters " + j+"/"+DIVIDE_INTO_NUM_FILES);
				psCurrentFile.println();
				psCurrentFile.println("Click on a filter for more info.");
				psCurrentFile.println();
				psCurrentFile.println("|Filter Name|Description|Package|");
				psCurrentFile.println("|-----------|-----------|-------|");
			}

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
				final int descriptionLength = description.length();

				description = description.substring(0,
					(descriptionLength > FilterIndexPrinter.MAX_DESC_LENGTH) ? (FilterIndexPrinter.MAX_DESC_LENGTH - 1)
						: (descriptionLength - 1));

				// Add open continuation marker.
				if (descriptionLength > FilterIndexPrinter.MAX_DESC_LENGTH) {
					description = description + " ...";
				}
			}

			psCurrentFile.println("|[" + f.shortName + "](filt." + f.fullName + ")|" + description + "|"
				+ f.fullName.substring(0, f.fullName.length() - f.shortName.length() - 1) + "|");
		}

		ps.println();
		ps.println("Run the class net.sf.jaer.util.FilterIndexPrinter to regenerate the lists. Save the lists to a name, e.g. filterlist. \nThe result will be a base file filterlist. Then copy the contents of filterlist here, and copy the contents of filterlist_1 to the index of filters, page 1, filterlist_2 to index of filters, page 2, etc.");

		ps.close();
		for (final PrintStream p : psAlpha) {
			p.close();
		}

		fout.close();
		for (final FileOutputStream f : foutAlpha) {
			f.close();
		}

		log.info("Done");
		System.exit(0);
	}
}
