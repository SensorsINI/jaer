/*
 * ClassChooserPanel2.java
 *
 * Created on May 13, 2007, 3:46 PM
 */
package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;

/**
 * A panel that finds subclasses of a class, displays them in a left list, displays another list given as a parameter
in the right panel, and accepts a list of default class names. The user can choose which classes and these are returned
by a call to getList.

 * @author  tobi
 */
public class ClassChooserPanel extends javax.swing.JPanel {
    Logger log=Logger.getLogger("net.sf.jaer.util");
    FilterableListModel chosenClassesListModel, availClassesListModel;
    ArrayList<String> revertCopy, defaultClassNames, availAllList, availFiltList;
//    MyBoundedJLabel myDescLabel=null;

    /** Creates new form ClassChooserPanel2
    
    @param subclassOf a Class that will be used to search the classpath for subclasses of subClassOf.
    @param classNames a list of names
    @param defaultClassNames the list on the right is replaced by this lixt if the user pushes the Defaults button.
    
     */
    public ClassChooserPanel(Class subclassOf, ArrayList<String> classNames, ArrayList<String> defaultClassNames) {
        initComponents();
        availFilterTextField.requestFocusInWindow();
        this.defaultClassNames = defaultClassNames;
        availAllList = SubclassFinder.findSubclassesOf(subclassOf.getName());
        availClassesListModel = new FilterableListModel(availAllList);
        availClassJList.setModel(availClassesListModel);
        availClassJList.setCellRenderer(new MyCellRenderer());
        Action addAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Object o = availClassJList.getSelectedValue();
                if (o == null) {
                    return;
                }
                int last = chosenClassesListModel.getSize() - 1;
                chosenClassesListModel.add(last + 1, o);
                classJList.setSelectedIndex(last + 1);
            }
        };
        addAction(availClassJList, addAction);
        Action removeAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                int index = classJList.getSelectedIndex();
                chosenClassesListModel.removeElementAt(index);
                int size = chosenClassesListModel.getSize();

                if (size == 0) { //Nobody's left, disable firing.

                    removeClassButton.setEnabled(false);

                } else { //Select an index.

                    if (index == chosenClassesListModel.getSize()) {
                        //removed item in last position
                        index--;
                    }

                    classJList.setSelectedIndex(index);
                    classJList.ensureIndexIsVisible(index);
                }
            }
        };
        addAction(classJList, removeAction);

        revertCopy = new ArrayList<String>(classNames);
        chosenClassesListModel = new FilterableListModel(classNames);
        classJList.setModel(chosenClassesListModel);
        classJList.setCellRenderer(new MyCellRenderer());
//        descPanel.add((myDescLabel=new MyBoundedJLabel()),BorderLayout.CENTER);
    }

    /** Fills in the descLabel with the description of the class. If the class implements a static method named <code>getDescription</code>
     * which returns a String, then the label text is set to the string. In addition, if the class implements a
     * method named <code>getDevelopmentStatus()</code> which returns an enum, then the string of the enum is all filled in.
     * @param evt
     * @param descLabel
     */
    private void showDescription(ListSelectionEvent evt, JLabel descLabel) {
        if(evt.getValueIsAdjusting()) return;
        try {
            // we need to check which list item was last selected
            JList list=(JList)evt.getSource();
            int ind=list.getSelectedIndex();
            if(ind<0){
                descLabel.setText("no description");
                return;
            }
            ListModel model=((JList) evt.getSource()).getModel();
            String className=(String)model.getElementAt(ind);
            String d=getClassDescription(className);
//            Font font=descLabel.getFont();
//            float fontSize=font.getSize2D();
            int nCharsThatFit=50; //(int)(descLabel.getWidth()/fontSize);
            if (d != null) { 
                if(d.length()>nCharsThatFit){
                    descLabel.setText(d.substring(0,nCharsThatFit)+"...");
                }else descLabel.setText(d);
                descLabel.setToolTipText(d);
            } else {
                descLabel.setText("no description");
            }
//            myDescLabel.setText(d);
            
        } catch (Exception e) {
            descLabel.setText("no description");
//            availClassDescriptionLabel.setText("exception getting description: "+e.getMessage());
            log.warning(e.toString());
        }
    }

    private class MyBoundedJLabel extends JLabel{
        private MyBoundedJLabel(){
            super();
        }

        @Override
        public void paint (Graphics g){
            if(g==null) return;
            Graphics2D g2=(Graphics2D)g;
            FontRenderContext frc=g2.getFontRenderContext();
            String s=getText();
            if(s!=null){
                Rectangle2D r=g2.getFont().getStringBounds(s,frc);
                if(r.getWidth()>getWidth()) setText(s.substring(0,60));
            }
            super.paint(g);
        }

    }
    
    private String getClassDescription(String className){
        try{
            Class c = Class.forName(className);
            Method m = c.getMethod("getDescription"); // makes warning about non-varargs call of varargs with inexact argument type
            String d = (String) (m.invoke(null)); 
            return d;
        }catch(Exception e){
            return null;
        }
   }

    private class MyCellRenderer extends JLabel implements ListCellRenderer {

     // This is the only method defined by ListCellRenderer.
     // We just reconfigure the JLabel each time we're called.

     public Component getListCellRendererComponent(
       JList list,              // the list
       Object value,            // value to display
       int index,               // cell index
       boolean isSelected,      // is the cell selected
       boolean cellHasFocus)    // does the cell have focus
     {
         String s = value.toString();
         setText(s);
//         setIcon((s.length() > 10) ? longIcon : shortIcon);
         if (isSelected) {
             setBackground(list.getSelectionBackground());
             setForeground(list.getSelectionForeground());
         } else {
             setBackground(list.getBackground());
             setForeground(list.getForeground());
         }
         if(getClassDescription(s)!=null){
             setForeground(Color.BLUE);
         }else{
             setForeground(list.getSelectionForeground());
         }
         setEnabled(list.isEnabled());
         setFont(list.getFont());
         setOpaque(true);
         return this;
     }
 }

    // extends DefaultListModel to add a text filter
    class FilterableListModel extends DefaultListModel {

        Vector origList = new Vector();
        String filterString = null;

        FilterableListModel(List<String> list) {
            super();
            for (String s : list) {
                this.addElement(s);
            }
            origList.addAll(list);
        }

        synchronized void resetList() {
            clear();
            for (Object o : origList) {
                addElement(o);
            }

        }

        synchronized void filter(String s) {
            filterString = s.toLowerCase();
            resetList();
            if (s == null || s.equals("")) {
                return;
            }
            Vector v = new Vector();  // list to prune out
            // must build a list of stuff to prune, then prune

            Enumeration en = elements();
            while (en.hasMoreElements()) {
                Object o = en.nextElement();
                String st = ((String) o).toLowerCase();
                int ind = st.indexOf(filterString);
                if (ind == -1) {
                    v.add(o);
                }
            }
            // prune list
            for (Object o : v) {
                removeElement(o);
            }
        }

        synchronized void clearFilter() {
            filter(null);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        availClassPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        availClassJList = new javax.swing.JList();
        filterLabel = new javax.swing.JLabel();
        availFilterTextField = new javax.swing.JTextField();
        availClassDescriptionLabel = new javax.swing.JLabel();
        chosenClassPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        classJList = new javax.swing.JList();
        classDescriptionLabel = new javax.swing.JLabel();
        addClassButton = new javax.swing.JButton();
        removeClassButton = new javax.swing.JButton();
        moveUpButton = new javax.swing.JButton();
        moveDownButton = new javax.swing.JButton();
        revertButton = new javax.swing.JButton();
        removeAllButton = new javax.swing.JButton();
        defaultsButton = new javax.swing.JButton();

        availClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Available classes"));

        jScrollPane1.setPreferredSize(new java.awt.Dimension(200, 100));

        availClassJList.setToolTipText("If your class doesn't show up here, rebuild the project to get it into jAER.jar (or some other jar on the classpath)");
        availClassJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                availClassJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(availClassJList);

        filterLabel.setText("Filter");

        availFilterTextField.setToolTipText("type any part of your filter name or description here to filter list");
        availFilterTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                availFilterTextFieldActionPerformed(evt);
            }
        });
        availFilterTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                availFilterTextFieldKeyTyped(evt);
            }
        });

        availClassDescriptionLabel.setText("description");

        javax.swing.GroupLayout availClassPanelLayout = new javax.swing.GroupLayout(availClassPanel);
        availClassPanel.setLayout(availClassPanelLayout);
        availClassPanelLayout.setHorizontalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availClassPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(availClassDescriptionLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 399, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(availClassPanelLayout.createSequentialGroup()
                        .addComponent(filterLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(availFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(68, 68, 68))
        );
        availClassPanelLayout.setVerticalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availClassPanelLayout.createSequentialGroup()
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterLabel)
                    .addComponent(availFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 487, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(availClassDescriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        chosenClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Class list"));

        jScrollPane2.setPreferredSize(new java.awt.Dimension(200, 100));

        classJList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        classJList.setToolTipText("These classes will be available to choose.");
        classJList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                classJListMouseClicked(evt);
            }
        });
        classJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                classJListValueChanged(evt);
            }
        });
        jScrollPane2.setViewportView(classJList);

        classDescriptionLabel.setText("description");

        javax.swing.GroupLayout chosenClassPanelLayout = new javax.swing.GroupLayout(chosenClassPanel);
        chosenClassPanel.setLayout(chosenClassPanelLayout);
        chosenClassPanelLayout.setHorizontalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(chosenClassPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(classDescriptionLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 414, Short.MAX_VALUE))
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE)
        );
        chosenClassPanelLayout.setVerticalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, chosenClassPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(classDescriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        addClassButton.setMnemonic('a');
        addClassButton.setText("Add");
        addClassButton.setToolTipText("Add the filter to the list of available filters");
        addClassButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addClassButtonActionPerformed(evt);
            }
        });

        removeClassButton.setMnemonic('r');
        removeClassButton.setText("Remove");
        removeClassButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeClassButtonActionPerformed(evt);
            }
        });

        moveUpButton.setMnemonic('u');
        moveUpButton.setText("Move up");
        moveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveUpButtonActionPerformed(evt);
            }
        });

        moveDownButton.setMnemonic('d');
        moveDownButton.setText("Move down");
        moveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveDownButtonActionPerformed(evt);
            }
        });

        revertButton.setMnemonic('e');
        revertButton.setText("Revert");
        revertButton.setToolTipText("Revert changes to the list");
        revertButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                revertButtonActionPerformed(evt);
            }
        });

        removeAllButton.setText("Remove all");
        removeAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAllButtonActionPerformed(evt);
            }
        });

        defaultsButton.setMnemonic('d');
        defaultsButton.setText("Defaults");
        defaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(availClassPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 421, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addClassButton, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chosenClassPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(removeAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE)
                    .addComponent(defaultsButton, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE)
                    .addComponent(moveUpButton, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE)
                    .addComponent(moveDownButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(removeClassButton)
                    .addComponent(revertButton, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {defaultsButton, moveDownButton, moveUpButton, removeAllButton, removeClassButton, revertButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(70, 70, 70)
                .addComponent(moveUpButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveDownButton)
                .addGap(33, 33, 33)
                .addComponent(removeClassButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeAllButton)
                .addGap(64, 64, 64)
                .addComponent(revertButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 200, Short.MAX_VALUE)
                .addComponent(defaultsButton)
                .addGap(83, 83, 83))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(chosenClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(17, 17, 17))
            .addGroup(layout.createSequentialGroup()
                .addGap(279, 279, 279)
                .addComponent(addClassButton)
                .addContainerGap(298, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(availClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(17, 17, 17))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void availFilterTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_availFilterTextFieldKeyTyped
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_availFilterTextFieldKeyTyped

    private void availFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_availFilterTextFieldActionPerformed
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_availFilterTextFieldActionPerformed

    private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
        chosenClassesListModel.clear();
        for (String s : defaultClassNames) {
            chosenClassesListModel.addElement(s);
        }
    }//GEN-LAST:event_defaultsButtonActionPerformed

    private void removeAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAllButtonActionPerformed
        chosenClassesListModel.clear();
    }//GEN-LAST:event_removeAllButtonActionPerformed

    private void revertButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_revertButtonActionPerformed
        chosenClassesListModel.clear();
        for (String s : revertCopy) {
            chosenClassesListModel.addElement(s);
        }
    }//GEN-LAST:event_revertButtonActionPerformed

    private void addClassButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addClassButtonActionPerformed
        Object o = availClassJList.getSelectedValue();
        if (o == null) {
            return;
        }
        int last = chosenClassesListModel.getSize() - 1;
        chosenClassesListModel.add(last + 1, o);
        classJList.setSelectedIndex(last + 1);
    }//GEN-LAST:event_addClassButtonActionPerformed

    private void moveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveDownButtonActionPerformed
        int last = chosenClassesListModel.getSize() - 1;
        int index = classJList.getSelectedIndex();
        if (index == last) {
            return;
        }
        Object o = chosenClassesListModel.getElementAt(index);
        chosenClassesListModel.removeElementAt(index);
        chosenClassesListModel.insertElementAt(o, index + 1);
        classJList.setSelectedIndex(index + 1);
    }//GEN-LAST:event_moveDownButtonActionPerformed

    private void moveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveUpButtonActionPerformed
        int index = classJList.getSelectedIndex();
        if (index == 0) {
            return;
        }
        Object o = chosenClassesListModel.getElementAt(index);
        chosenClassesListModel.removeElementAt(index);
        chosenClassesListModel.insertElementAt(o, index - 1);
        classJList.setSelectedIndex(index - 1);
    }//GEN-LAST:event_moveUpButtonActionPerformed

    private void removeClassButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeClassButtonActionPerformed
        int index = classJList.getSelectedIndex();
        chosenClassesListModel.removeElementAt(index);
        int size = chosenClassesListModel.getSize();

        if (size == 0) { //Nobody's left, disable firing.

            removeClassButton.setEnabled(false);

        } else { //Select an index.

            if (index == chosenClassesListModel.getSize()) {
                //removed item in last position
                index--;
            }

            classJList.setSelectedIndex(index);
            classJList.ensureIndexIsVisible(index);
        }
    }//GEN-LAST:event_removeClassButtonActionPerformed

    private void classJListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_classJListMouseClicked
//        System.out.println(classJList.getSelectedValue());
        moveDownButton.setEnabled(true);
        moveUpButton.setEnabled(true);
    }//GEN-LAST:event_classJListMouseClicked

private void availClassJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_availClassJListValueChanged
    showDescription(evt, availClassDescriptionLabel);
}//GEN-LAST:event_availClassJListValueChanged

private void classJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_classJListValueChanged
    showDescription(evt, classDescriptionLabel);
}//GEN-LAST:event_classJListValueChanged
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addClassButton;
    private javax.swing.JLabel availClassDescriptionLabel;
    private javax.swing.JList availClassJList;
    private javax.swing.JPanel availClassPanel;
    private javax.swing.JTextField availFilterTextField;
    private javax.swing.JPanel chosenClassPanel;
    private javax.swing.JLabel classDescriptionLabel;
    private javax.swing.JList classJList;
    private javax.swing.JButton defaultsButton;
    private javax.swing.JLabel filterLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton moveDownButton;
    private javax.swing.JButton moveUpButton;
    private javax.swing.JButton removeAllButton;
    private javax.swing.JButton removeClassButton;
    private javax.swing.JButton revertButton;
    // End of variables declaration//GEN-END:variables
    // from http://forum.java.sun.com/thread.jspa?forumID=57&threadID=626866
    private static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

    public static void addAction(JList source, Action action) {
        //  Handle enter key

        InputMap im = source.getInputMap();
        im.put(ENTER, ENTER);
        source.getActionMap().put(ENTER, action);

        //  Handle mouse double click

        source.addMouseListener(new ActionMouseListener());
    }
    //  Implement Mouse Listener
    static class ActionMouseListener extends MouseAdapter {

        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                JList list = (JList) e.getSource();
                Action action = list.getActionMap().get(ENTER);

                if (action != null) {
                    ActionEvent event = new ActionEvent(
                            list,
                            ActionEvent.ACTION_PERFORMED,
                            "");
                    action.actionPerformed(event);
                }
            }
        }
    }
}
