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
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeSupport;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.apache.commons.lang3.RandomStringUtils;

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
    String word = null;
    WordSearcher searcher = null;

    private void f3pressed(KeyEvent evt) {
        int code = evt.getKeyCode();
        boolean f3 = (code == java.awt.event.KeyEvent.VK_F3);
        boolean shift = evt.isShiftDown();
        if (f3) {
            if (!shift) {
                searcher.searchNext();
            } else {
                searcher.searchPrevious();
            }
        }
    }

    private void findFirst() {
        if (searcher == null) {
            return;
        }
        word = findTF.getText();
        if (word == null || word == "") {
            return;
        }

        int caret = findTF.hasFocus() ? 0 : pane.getCaretPosition(); // if in find textfield, search from start of doc, if in pane, search in pane starting from point
        int offset = searcher.searchFirst(word, caret, true);
        setFindFieldColorAndScroll(offset);
    }

    private void findNext() {

        if (searcher == null) {
            return;
        }
        int offset = searcher.searchNext();
        setFindFieldColorAndScroll(offset);
    }

    private void findPrev() {
        if (searcher == null) {
            return;
        }
        int offset = searcher.searchPrevious();
        setFindFieldColorAndScroll(offset);

    }

    private void setFindFieldColorAndScroll(int offset) {
        // System.out.println("offset="+offset);
        if (offset > 0) {
            findTF.setForeground(Color.black);
            try {
                pane.scrollRectToVisible(pane.modelToView2D(offset).getBounds());
            } catch (BadLocationException e) {
            }
        } else if (offset < 0) {
            findTF.setForeground(Color.red);
        }
    }
    /**
     * Maximum document length in characters. If the document gets larger than
     * this it is cleared. This should prevent OutOfMemory errors during long
     * runs.
     */
    public final int MAX_CHARS = 80 * 80 * 400; // lines*lines/page*pages

    /**
     * Creates new form AEViewerConsoleOutputFrame
     */
    public AEViewerConsoleOutputFrame() {
        initComponents();
        attr = pane.getInputAttributes();
        doc = pane.getStyledDocument();
        searcher = new WordSearcher(pane);

//        findTF.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent evt) {
//                 System.out.println("findTF text:"+word);
//                findFirst(); // already handled enter by keyTyped
//            }
//
//        });
        findTF.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                System.out.println("findTF text:" + findTF.getText());

                if (evt.getKeyChar() == '\n') {
                    findNext();
                } else {
                    findFirst();
                }
            }
        });

//        findTF.addKeyListener(new java.awt.event.KeyAdapter() {
//            @Override
//            public void keyPressed(java.awt.event.KeyEvent evt) {
//                f3pressed(evt);
//
//            }
//        });
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent evt) {
//                searcher.searchFirst(word);
            }

            @Override
            public void removeUpdate(DocumentEvent evt) {
//                searcher.searchFirst(word);
            }

            @Override
            public void changedUpdate(DocumentEvent evt) {
            }
        });

        pane.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                f3pressed(evt);

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
                    } else {
                        setInfo();
                    }
                    boolean tail = pane.getCaretPosition() == doc.getLength() ? true : false;
                    doc.insertString(doc.getLength(), s, attr);
                    if (tail) {
                        pane.setCaretPosition(doc.getLength());
                    }
                } catch (BadLocationException ex) {
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
                } catch (BadLocationException ex) {
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
        next = new BasicArrowButton(BasicArrowButton.SOUTH);
        prev = new BasicArrowButton(BasicArrowButton.NORTH);
        clearSeachB = new javax.swing.JButton();

        setTitle("jAER Console");

        closeButton.setMnemonic('c');
        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        clearButton.setMnemonic('r');
        clearButton.setText("Clear");
        clearButton.setToolTipText("Clear contents (Ctl-L)");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        pane.setEditable(false);
        pane.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                paneKeyReleased(evt);
            }
        });
        jScrollPane1.setViewportView(pane);

        findLabel.setText("Highlight");

        findTF.setToolTipText("Highlights a string.");

        next.setToolTipText("go to next (F3)");
        next.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextActionPerformed(evt);
            }
        });

        prev.setToolTipText("go to previous (Shift+F3)");
        prev.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevActionPerformed(evt);
            }
        });

        clearSeachB.setText("X");
        clearSeachB.setToolTipText("Clears search box");
        clearSeachB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearSeachBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(findLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(findTF, javax.swing.GroupLayout.PREFERRED_SIZE, 335, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearSeachB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(next, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(prev, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 90, Short.MAX_VALUE)
                        .addComponent(clearButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(closeButton))
                    .addComponent(jScrollPane1))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 383, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(next, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(prev, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(clearButton)
                        .addComponent(findLabel)
                        .addComponent(findTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(clearSeachB))
                    .addComponent(closeButton, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void nextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextActionPerformed
        findNext();
    }//GEN-LAST:event_nextActionPerformed

    private void prevActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevActionPerformed
        findPrev();
    }//GEN-LAST:event_prevActionPerformed

    private void clearSeachBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearSeachBActionPerformed
        findTF.setText("");
        findTF.requestFocus();
    }//GEN-LAST:event_clearSeachBActionPerformed

    private void paneKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_paneKeyReleased
                int code = evt.getKeyCode();
        boolean lkey = (code == java.awt.event.KeyEvent.VK_L);
        boolean ctl = evt.isControlDown();
        if (lkey && ctl) {
           clear();
        }
    }//GEN-LAST:event_paneKeyReleased

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_closeButtonActionPerformed
        setVisible(false);
    }// GEN-LAST:event_closeButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_clearButtonActionPerformed
        clear();
    }// GEN-LAST:event_clearButtonActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        final AEViewerConsoleOutputFrame frame = new AEViewerConsoleOutputFrame();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        Random r = new Random();
        while (true) {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                System.out.println("interrupted");
                break;
            }
            int n = r.nextInt(80);
            String s = RandomStringUtils.randomPrint(n);
            if (r.nextInt(100) < 10) {
                s += "       tobi    ";
            }
            s += "\n";
            boolean warning = r.nextInt(10) < 2;
            if (warning) {
                frame.append(s, Level.WARNING);
            } else {
                frame.append(s, Level.INFO);
            }
        }

        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {

//                frame.append(word, Level.SEVERE);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton clearButton;
    private javax.swing.JButton clearSeachB;
    private javax.swing.JButton closeButton;
    private javax.swing.JLabel findLabel;
    private javax.swing.JTextField findTF;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton next;
    private javax.swing.JTextPane pane;
    private javax.swing.JButton prev;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

}
