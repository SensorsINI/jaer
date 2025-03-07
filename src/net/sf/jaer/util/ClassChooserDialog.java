
package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.jaer.chip.AEChip;

/** A modal dialog that shows a list of source classes (found from the classpath) and a list of String names of classes and lets
 the user shuffle them from one side to the other and reorder the chosen class names.
 Use it by constructing a new instance, making it visible (this call will magically block until
 the user presses OK or Cancel), and then calling <code>getReturnValue()</code>.
 
 * @author  tobi */
public class ClassChooserDialog extends javax.swing.JDialog {
    /** A return status code - returned if Cancel button has been pressed */
    public static final int RET_CANCEL = 0;
    /** A return status code - returned if OK button has been pressed */
    public static final int RET_OK = 1;
    
    private final ClassChooserPanel chooserPanel;
    
    /** Creates new model dialog ClassChooserDialog.
     * @param parent parent Frame
     @param subclassOf a Class that will be used to search the classpath for sublasses of this class; these class names are displayed on the left.
     @param classNames a list of class names that are already chosen, displayed on the right.
     @param defaultClassNames the defaults passed to ClassChooserPanel which replace the chosen list if the Defaults button is pressed.
     */
    public ClassChooserDialog(Frame parent, Class subclassOf, ArrayList<String> classNames, ArrayList<String> defaultClassNames) {
        super(parent, true);
        initComponents();
//        cancelButton.requestFocusInWindow();
        chooserPanel=new ClassChooserPanel(subclassOf,classNames,defaultClassNames);
        businessPanel.add(chooserPanel,BorderLayout.CENTER);
        pack();
        //  Handle escape key to close the dialog
        // from http://forum.java.sun.com/thread.jspa?threadID=462776&messageID=2123119
        
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                dispose();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
    }
    
    /** @return the return status of this dialog - one of RET_OK or RET_CANCEL */
    public int getReturnStatus() {
        return returnStatus;
    }

    /** Returns list of classes that should populate the users choices.
     * 
     * @return list of class names, fully qualified with package path 
     */
    public ArrayList<String> getList(){
        if(returnStatus==RET_CANCEL) return null;
        Object[] oa=chooserPanel.getChosenClassesListModel().toArray();
        ArrayList<String> ret=new ArrayList(Arrays.asList(oa));
        return ret;
    }
    
    /** Return the last class added if any.
     * 
     * @return fully-qualified class name, or null if none added.
     */
    public String getLastSelectedClass(){
        return chooserPanel.getLastSelectedClassName();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        businessPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("ClassChooser");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                formKeyTyped(evt);
            }
        });

        okButton.setMnemonic('o');
        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        businessPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 447, Short.MAX_VALUE)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(businessPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(businessPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    private void formKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyTyped
//        System.out.println(evt);
        if(evt.getKeyCode()==java.awt.event.KeyEvent.VK_ESCAPE) doClose(RET_CANCEL);
    }//GEN-LAST:event_formKeyTyped
    
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_okButtonActionPerformed
    
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doClose(RET_CANCEL);
    }//GEN-LAST:event_cancelButtonActionPerformed
    
    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        doClose(RET_CANCEL);
    }//GEN-LAST:event_closeDialog
    
    private void doClose(int retStatus) {
        returnStatus = retStatus;
        setVisible(false);
        dispose();
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override public void run() {
                ArrayList<String> classNames=new ArrayList<>();
                ClassChooserDialog dlg=new ClassChooserDialog(new javax.swing.JFrame(), AEChip.class,classNames,new ArrayList<String>());
                int ret;
                do{
                    dlg.setVisible(true);
                    ret=dlg.getReturnStatus();
//                    System.out.println("ret="+ret);
                }while(ret!=ClassChooserDialog.RET_CANCEL);
                System.exit(0);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel businessPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton okButton;
    // End of variables declaration//GEN-END:variables
    
    private int returnStatus = RET_CANCEL;
}
