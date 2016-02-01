/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * AEViewerConsoleOutputFrame.java
 *
 * Created on Feb 1, 2009, 7:18:36 PM
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeSupport;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * A window used to show Logger output.
 * <p>
 * Generates PropertyChangeEvent "cleared" when viewer is cleared.
 *
 *
 * @author tobi
 */
public class AEViewerConsoleOutputFrame extends javax.swing.JFrame {

	// final Level[] levels = {Level.OFF, Level.INFO, Level.WARNING};
	private final MutableAttributeSet attr;
	private final StyledDocument doc;

	private PropertyChangeSupport support = new PropertyChangeSupport(this);
	private int lastFindPos = -1;
	private int caretPosition = 0;
	private int lastOffset = -1;
	String word = null;

	/**
	 * Maximum document length in characters. If the document gets larger than
	 * this it is cleared. This should prevent OutOfMemory errors during long
	 * runs.
	 */
	public final int MAX_CHARS = 80 * 80 * 100; // lines*lines/page*pages

	/**
	 * Creates new form AEViewerConsoleOutputFrame
	 */
	public AEViewerConsoleOutputFrame() {
		initComponents();
		attr = pane.getInputAttributes();
		doc = pane.getStyledDocument();
		final WordSearcher searcher = new WordSearcher(pane);

		findTF.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				word = findTF.getText();
				int offset = searcher.search(word);
				// System.out.println("offset="+offset);
				if (offset > 0) {
					findTF.setForeground(Color.black);
					try {
						pane.scrollRectToVisible(pane.modelToView(offset));
					}
					catch (BadLocationException e) {
					}
				}
				else if (offset < 0) {
					findTF.setForeground(Color.red);
				}
			}
		});
		findTF.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyTyped(java.awt.event.KeyEvent evt) {

				word = findTF.getText();
				int offset = searcher.search(word);
				if (offset > 0) {
					findTF.setForeground(Color.black);
					try {
						pane.scrollRectToVisible(pane.modelToView(offset));
					}
					catch (BadLocationException e) {
					}
				}
				else if (offset < 0) {
					findTF.setForeground(Color.red);
				}
			}
		});

		// findTF.addKeyListener(new java.awt.event.KeyAdapter() {
		// public void keyPressed(java.awt.event.KeyEvent evt) {
		// int code = evt.getKeyCode();
		// switch (code) {
		// case java.awt.event.KeyEvent.VK_F3:
		// int offset = searcher.search(word);
		// if (offset != -1) {
		// try {
		// pane.scrollRectToVisible(pane
		// .modelToView(offset));
		// } catch (BadLocationException e) {
		// }
		// }
		// }
		//
		// }
		// });
		pane.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent evt) {
				searcher.search(word);
			}

			@Override
			public void removeUpdate(DocumentEvent evt) {
				searcher.search(word);
			}

			@Override
			public void changedUpdate(DocumentEvent evt) {
			}
		});

		pane.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt) {
				int code = evt.getKeyCode();
				switch (code) {
					case java.awt.event.KeyEvent.VK_F3:
						int offset = searcher.search(word);
						if (offset != -1) {
							try {
								pane.scrollRectToVisible(pane.modelToView(offset));
							}
							catch (BadLocationException e) {
							}
						}
				}

			}
		});

		// levelComboxBox.removeAllItems();
		// for (Level l : levels) {
		// levelComboxBox.addItem(l.getName());
		// }
	}

	/**
	 * Applies to next append
	 */
	private void setWarning() {
		StyleConstants.setForeground(attr, Color.red);
	}

	/**
	 * Applies to next append
	 */
	private void setInfo() {
		StyleConstants.setForeground(attr, Color.black);
	}

	/**
	 * Appends the message using the level to set the style
	 */
	public void append(final String s, final Level level) {
		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					if (doc.getLength() > MAX_CHARS) {
						doc.remove(0, doc.getLength());
						String s = new Date() + ": cleared log to prevent OutOfMemory, increase MAX_CHARS (currently " + MAX_CHARS
							+ ") to save more logging";
						doc.insertString(0, s, attr);
					}
					if (level.intValue() > Level.INFO.intValue()) {
						setWarning();
					}
					else {
						setInfo();
					}
					boolean tail = pane.getCaretPosition() == doc.getLength() ? true : false;
					doc.insertString(doc.getLength(), s, attr);
					if (tail) {
						pane.setCaretPosition(doc.getLength());
					}
				}
				catch (BadLocationException ex) {
					Logger.getLogger(AEViewerConsoleOutputFrame.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});
	}

	public void clear() {
		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					support.firePropertyChange("cleared", null, null);
					doc.remove(0, doc.getLength());
					// txtTextLog.setText(null);
				}
				catch (BadLocationException ex) {
					Logger.getLogger(AEViewerConsoleOutputFrame.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
	// <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		closeButton = new javax.swing.JButton();
		clearButton = new javax.swing.JButton();
		jScrollPane1 = new javax.swing.JScrollPane();
		pane = new javax.swing.JTextPane();
		findLabel = new javax.swing.JLabel();
		findTF = new javax.swing.JTextField();

		setTitle("jAER Console");

		closeButton.setMnemonic('c');
		closeButton.setText("Close");
		closeButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				closeButtonActionPerformed(evt);
			}
		});

		clearButton.setMnemonic('r');
		clearButton.setText("Clear");
		clearButton.setToolTipText("Clear contents");
		clearButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				clearButtonActionPerformed(evt);
			}
		});

		jScrollPane1.setViewportView(pane);

		findLabel.setText("Highlight");

		findTF.setToolTipText("Highlights a string.");

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
				layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
						.addGroup(layout.createSequentialGroup().addContainerGap().addComponent(jScrollPane1,
							javax.swing.GroupLayout.DEFAULT_SIZE, 687, Short.MAX_VALUE))
				.addGroup(layout.createSequentialGroup().addGap(58, 58, 58).addComponent(findLabel).addGap(18, 18, 18)
					.addComponent(findTF, javax.swing.GroupLayout.PREFERRED_SIZE, 335, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE,
						Short.MAX_VALUE)
					.addComponent(clearButton).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
					.addComponent(closeButton))).addContainerGap()));
		layout.setVerticalGroup(
			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
				layout.createSequentialGroup().addContainerGap()
					.addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 383, Short.MAX_VALUE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
					.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
							layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(clearButton)
								.addComponent(findLabel).addComponent(findTF, javax.swing.GroupLayout.PREFERRED_SIZE,
									javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
						.addComponent(closeButton, javax.swing.GroupLayout.Alignment.TRAILING))
					.addContainerGap()));

		pack();
	}// </editor-fold>//GEN-END:initComponents

	private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_closeButtonActionPerformed
		setVisible(false);
	}// GEN-LAST:event_closeButtonActionPerformed

	private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_clearButtonActionPerformed
		clear();
	}// GEN-LAST:event_clearButtonActionPerformed

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String args[]) {
		java.awt.EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				new AEViewerConsoleOutputFrame().setVisible(true);
			}
		});
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JButton clearButton;
	private javax.swing.JButton closeButton;
	private javax.swing.JLabel findLabel;
	private javax.swing.JTextField findTF;
	private javax.swing.JScrollPane jScrollPane1;
	private javax.swing.JTextPane pane;
	// End of variables declaration//GEN-END:variables

	/**
	 * @return the support
	 */
	public PropertyChangeSupport getSupport() {
		return support;
	}

}
