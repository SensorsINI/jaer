/*
 * ClassChooserPanel2.java
 *
 * Created on May 13, 2007, 3:46 PM
 */
package net.sf.jaer.util;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ProgressMonitor;
import net.sf.jaer.Description;
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
import java.beans.PropertyChangeListener;
import java.lang.reflect.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import net.sf.jaer.DevelopmentStatus;

/**
 * A panel that finds subclasses of a class, displays them in a left list,
 * displays another list given as a parameter in the right panel, and accepts a
 * list of default class names. The user can choose which classes and these are
 * returned by a call to getList.
 *
 * @author tobi
 */
public class ClassChooserPanel extends javax.swing.JPanel {

    Logger log = Logger.getLogger("net.sf.jaer.util");
    FilterableListModel chosenClassesListModel, availClassesListModel;
    ArrayList<String> revertCopy, defaultClassNames, availAllList, availFiltList;
//    MyBoundedJLabel myDescLabel=null;
    private String MISSING_DESCRIPTION_MESSAGE = "<html>No description available - provide one using @Description annotation, as in <pre>@Description(\"Example class\") \n public class MyClass</pre></html>";

    private class ClassDescription {

        String description = null;
        DevelopmentStatus.Status developmentStatus = null;

        public ClassDescription(String description, DevelopmentStatus.Status developmentStatus) {
            this.description = description;
            this.developmentStatus = developmentStatus;
        }
    }

    class DescriptionMap extends HashMap<String, ClassDescription> {

        ClassDescription get(String name) {
            if (name == null) {
                return null;
            }
            if (super.get(name) == null) {
                put(name);
            }
            return super.get(name);
        }

        void put(String name) {
            if (name == null) {
                return;
            }
            if (containsKey(name)) {
                return;
            }
            try {
                Class c = Class.forName(name);
                if (c == null) {
                    log.warning("tried to put class " + name + " but there is no such class");
                    return;
                }
                String descriptionString = null;
                if (c.isAnnotationPresent(Description.class)) {
                    Description des = (Description) c.getAnnotation(Description.class);
                    descriptionString = des.value();
                }
                DevelopmentStatus.Status devStatus = null;
                if (c.isAnnotationPresent(DevelopmentStatus.class)) {
                    DevelopmentStatus des = (DevelopmentStatus) c.getAnnotation(DevelopmentStatus.class);
                    devStatus = des.value();
                }

                ClassDescription des = new ClassDescription(descriptionString, devStatus);
                put(name, des);
            } catch (Exception e) {
                log.warning("trying to put class named " + name + " caught " + e.toString());
            }

        }
    }
    private DescriptionMap descriptionMap = new DescriptionMap();

    /**
     * Creates new form ClassChooserPanel
     *
     * @param subclassOf a Class that will be used to search the classpath for
     * subclasses of subClassOf.
     * @param classNames a list of names, which is filled in by the actions of
     * the user with the chosen classes
     * @param defaultClassNames the list on the right is replaced by this lixt
     * if the user pushes the Defaults button.
     *
     */
    public ClassChooserPanel(final Class subclassOf, ArrayList<String> classNames, ArrayList<String> defaultClassNames) {
        initComponents();
        availFilterTextField.requestFocusInWindow();
        this.defaultClassNames = defaultClassNames;
        final SubclassFinder.SubclassFinderWorker worker = new SubclassFinder.SubclassFinderWorker(subclassOf);
        final DefaultListModel tmpList = new DefaultListModel();
        tmpList.addElement("scanning...");
        availClassJList.setModel(tmpList);
        worker.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
//                System.out.println(evt.getPropertyName() + "  " + evt.getNewValue());
                if (evt != null && evt.getNewValue().equals(SwingWorker.StateValue.DONE)) {
                    try {
                        availAllList = worker.get();
                        if (availAllList == null) {
                            log.warning("got empty list of classes - something wrong here, aborting dialog");
                            return;
                        }
                        Collections.sort(availAllList, new ClassNameSorter());
                        availClassesListModel = new FilterableListModel(availAllList);
                        availClassJList.setModel(availClassesListModel);
                        availClassJList.setCellRenderer(new MyCellRenderer());
                        Action addAction = new AbstractAction() {

                            @Override
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
                    } catch (Exception ex) {
                        Logger.getLogger(ClassChooserPanel.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                } else if (evt != null && evt.getNewValue() instanceof Integer) {
                    int progress = (Integer) evt.getNewValue();
                    String s = String.format("Scanning %d/100...", progress);
                    tmpList.removeAllElements();
                    tmpList.addElement(s);
                }
            }
        });
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();

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

    public JPanel getFilterTypeOptionsPanel() {
        return filterTypeOptionsPanel;
    }

    private class ClassNameSorter implements Comparator {

        public int compare(Object o1, Object o2) {
            if (o1 instanceof String && o2 instanceof String) {
                return shortName((String) o1).compareTo(shortName((String) o2));
            } else {
                return -1;
            }
        }
    }

    /**
     * Fills in the descLabel with the description of the class. If the class
     * implements a static method named
     * <code>getDescription</code> which returns a String, then the label text
     * is set to the string. In addition, if the class implements a method named
     * <code>getDevelopmentStatus()</code> which returns an enum, then the
     * string of the enum is all filled in.
     *
     * @param evt
     * @param area
     */
    private void showDescription(ListSelectionEvent evt, JTextPane area) {
        if (evt.getValueIsAdjusting()) {
            return;
        }
        try {
            // we need to check which list item was last selected
            JList list = (JList) evt.getSource();
            int ind = list.getSelectedIndex();
            if (ind < 0) {
                area.setText(MISSING_DESCRIPTION_MESSAGE);
                return;
            }
            ListModel model = ((JList) evt.getSource()).getModel();
            String className = (String) model.getElementAt(ind);
            String shortClassName = className.substring(className.lastIndexOf(".") + 1);
            String des = getClassDescription(className);
            if (des == null) {
                des = MISSING_DESCRIPTION_MESSAGE;
            }
            String d = "<html>" + shortClassName + ": " + des;
            area.setContentType("text/html");
            area.setText(d);
        } catch (Exception e) {
            area.setText(MISSING_DESCRIPTION_MESSAGE);
            log.warning(e.toString());
        }
    }

    private class MyBoundedJLabel extends JLabel {

        private MyBoundedJLabel() {
            super();
        }

        @Override
        public void paint(Graphics g) {
            if (g == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            FontRenderContext frc = g2.getFontRenderContext();
            String s = getText();
            if (s != null) {
                Rectangle2D r = g2.getFont().getStringBounds(s, frc);
                if (r.getWidth() > getWidth()) {
                    setText(s.substring(0, 60));
                }
            }
            super.paint(g);
        }
    }

    private DevelopmentStatus.Status getClassDevelopmentStatus(String className) {
        ClassDescription des = descriptionMap.get(className);
        if (des == null) {
            return null;
        }
        return des.developmentStatus;
    }

    private String getClassDescription(String className) {
        ClassDescription des = descriptionMap.get(className);
        if (des == null) {
            return null;
        }
        return des.description;
    }

    private class MyCellRenderer extends JLabel implements ListCellRenderer {
        // This is the only method defined by ListCellRenderer.
        // We just reconfigure the JLabel each time we're called.

        public Component getListCellRendererComponent(
                JList list, // the list
                Object obj, // value to display
                int index, // cell index
                boolean isSelected, // is the cell selected
                boolean cellHasFocus) // does the cell have focus
        {
            String fullclassname = obj.toString();
            String shortname = fullclassname.substring(fullclassname.lastIndexOf('.') + 1);
//            String s = value.toString(); // full class name
            setText(shortname);
            Color foreground, background;
//         setIcon((s.length() > 10) ? longIcon : shortIcon);
//            log.info("rendering " + obj);
//            if(!cellHasFocus) return this; // not visible
            if (isSelected) {
                background = list.getSelectionBackground();
                if (getClassDescription(fullclassname) != null) {
                    DevelopmentStatus.Status develStatus = null;
                    if ((develStatus = getClassDevelopmentStatus(fullclassname)) == DevelopmentStatus.Status.Experimental) {
                        foreground = Color.ORANGE;
                        develStatusTF.setText(develStatus.toString());
                    } else {
                        foreground = Color.BLUE;
                        develStatusTF.setText("unknown");
                    }
                    setToolTipText(fullclassname + ": " + getClassDescription(fullclassname));
                } else {
                    foreground = list.getSelectionForeground();
                    setToolTipText(fullclassname);
                }
            } else {
                background = list.getBackground();
                foreground = list.getForeground();
                if (getClassDescription(fullclassname) != null) {
                    if ((getClassDevelopmentStatus(fullclassname)) == DevelopmentStatus.Status.Experimental) {
                        foreground = Color.ORANGE;
                    } else {
                        foreground = Color.BLUE;
                    }
                } else {
                    foreground = list.getSelectionForeground();
                }
            }
            setEnabled(list.isEnabled());
//            setFont(list.getFont());
            setOpaque(true);
            setForeground(foreground);
            setBackground(background);
            return this;
        }
    }

    private String shortName(String s) {
        int i = s.lastIndexOf('.');
        if (i < 0 || i == s.length() - 1) {
            return s;
        }
        return s.substring(i + 1);
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
            if (s == null) {
                resetList();
                return;
            }
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        availClassPanel = new javax.swing.JPanel();
        filterPanel = new javax.swing.JPanel();
        filterLabel = new javax.swing.JLabel();
        availFilterTextField = new javax.swing.JTextField();
        filterTypeOptionsPanel = new javax.swing.JPanel();
        clearFilterBut = new javax.swing.JButton();
        availClassDesciptionPanel = new javax.swing.JScrollPane();
        availClassJList = new javax.swing.JList();
        chosenClassPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        classJList = new javax.swing.JList();
        addClassButton = new javax.swing.JButton();
        removeClassButton = new javax.swing.JButton();
        moveUpButton = new javax.swing.JButton();
        moveDownButton = new javax.swing.JButton();
        revertButton = new javax.swing.JButton();
        removeAllButton = new javax.swing.JButton();
        defaultsButton = new javax.swing.JButton();
        descPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        descPane = new javax.swing.JTextPane();
        jLabel1 = new javax.swing.JLabel();
        develStatusTF = new javax.swing.JTextField();

        availClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Available classes"));
        availClassPanel.setToolTipText("If your class doesn't show up here, rebuild the project to get it into jAER.jar (or some other jar on the classpath)");
        availClassPanel.setPreferredSize(new java.awt.Dimension(400, 300));

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

        clearFilterBut.setText("X");
        clearFilterBut.setIconTextGap(0);
        clearFilterBut.setMargin(new java.awt.Insets(2, 1, 1, 2));
        clearFilterBut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearFilterButActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout filterPanelLayout = new javax.swing.GroupLayout(filterPanel);
        filterPanel.setLayout(filterPanelLayout);
        filterPanelLayout.setHorizontalGroup(
            filterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(filterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(filterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(filterPanelLayout.createSequentialGroup()
                        .addComponent(filterLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(availFilterTextField))
                    .addComponent(filterTypeOptionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearFilterBut))
        );
        filterPanelLayout.setVerticalGroup(
            filterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(filterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(filterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterLabel)
                    .addComponent(availFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(clearFilterBut))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(filterTypeOptionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(42, 42, 42))
        );

        availClassDesciptionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Available classes"));

        availClassJList.setToolTipText("If your class doesn't show up here, rebuild the project to get it into jAER.jar (or some other jar on the classpath)");
        availClassJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                availClassJListValueChanged(evt);
            }
        });
        availClassDesciptionPanel.setViewportView(availClassJList);
        availClassJList.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout availClassPanelLayout = new javax.swing.GroupLayout(availClassPanel);
        availClassPanel.setLayout(availClassPanelLayout);
        availClassPanelLayout.setHorizontalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availClassPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(availClassDesciptionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 296, Short.MAX_VALUE)
                    .addComponent(filterPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        availClassPanelLayout.setVerticalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, availClassPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filterPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(availClassDesciptionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 413, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        chosenClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Class list"));
        chosenClassPanel.setToolTipText("These classes will be available to choose.");
        chosenClassPanel.setPreferredSize(new java.awt.Dimension(400, 300));

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
        classJList.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout chosenClassPanelLayout = new javax.swing.GroupLayout(chosenClassPanel);
        chosenClassPanel.setLayout(chosenClassPanelLayout);
        chosenClassPanelLayout.setHorizontalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(chosenClassPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 427, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        chosenClassPanelLayout.setVerticalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(chosenClassPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 286, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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

        descPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Class description"));

        descPane.setEditable(false);
        jScrollPane1.setViewportView(descPane);

        jLabel1.setText("Development status: ");

        develStatusTF.setEditable(false);
        develStatusTF.setToolTipText("Shows DevelopmentStatus of class as annotated with DevelopmentStatus");

        javax.swing.GroupLayout descPanelLayout = new javax.swing.GroupLayout(descPanel);
        descPanel.setLayout(descPanelLayout);
        descPanelLayout.setHorizontalGroup(
            descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(descPanelLayout.createSequentialGroup()
                .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 585, Short.MAX_VALUE)
                    .addGroup(descPanelLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(develStatusTF, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE)))
                .addContainerGap())
        );
        descPanelLayout.setVerticalGroup(
            descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(descPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(develStatusTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(availClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(addClassButton, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(chosenClassPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 449, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(removeAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(moveUpButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(moveDownButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(removeClassButton)
                            .addComponent(revertButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(defaultsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(descPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {defaultsButton, moveDownButton, moveUpButton, removeAllButton, removeClassButton, revertButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(112, 112, 112)
                        .addComponent(addClassButton))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(chosenClassPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(descPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(27, 27, 27)
                                .addComponent(availClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 509, Short.MAX_VALUE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(58, 58, 58)
                        .addComponent(moveUpButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(moveDownButton)
                        .addGap(33, 33, 33)
                        .addComponent(removeClassButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeAllButton)
                        .addGap(18, 18, 18)
                        .addComponent(revertButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(defaultsButton)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void availFilterTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_availFilterTextFieldKeyTyped
        if (availClassesListModel == null) {
            return;
        }
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_availFilterTextFieldKeyTyped

    private void availFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_availFilterTextFieldActionPerformed
        if (availClassesListModel == null) {
            return;
        }
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
        moveDownButton.setEnabled(true);
        moveUpButton.setEnabled(true);
    }//GEN-LAST:event_classJListMouseClicked

private void availClassJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_availClassJListValueChanged
    showDescription(evt, descPane);
}//GEN-LAST:event_availClassJListValueChanged

private void classJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_classJListValueChanged
    showDescription(evt, descPane);
}//GEN-LAST:event_classJListValueChanged

    private void clearFilterButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearFilterButActionPerformed
        availFilterTextField.setText("");
        availClassesListModel.clearFilter();
    }//GEN-LAST:event_clearFilterButActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addClassButton;
    private javax.swing.JScrollPane availClassDesciptionPanel;
    private javax.swing.JList availClassJList;
    private javax.swing.JPanel availClassPanel;
    private javax.swing.JTextField availFilterTextField;
    private javax.swing.JPanel chosenClassPanel;
    private javax.swing.JList classJList;
    private javax.swing.JButton clearFilterBut;
    private javax.swing.JButton defaultsButton;
    private javax.swing.JTextPane descPane;
    private javax.swing.JPanel descPanel;
    private javax.swing.JTextField develStatusTF;
    private javax.swing.JLabel filterLabel;
    private javax.swing.JPanel filterPanel;
    private javax.swing.JPanel filterTypeOptionsPanel;
    private javax.swing.JLabel jLabel1;
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
