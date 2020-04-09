/* ClassChooserPanel2.java
 *
 * Created on May 13, 2007, 3:46 PM */
package net.sf.jaer.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

/**
 * A panel that finds subclasses of a class, displays them in a left list,
 * displays another list given as a parameter in the right panel, and accepts a
 * list of default class names. The user can choose which classes and these are
 * returned by a call to getList. The list of available classes is built in the
 * background.
 *
 * @author tobi
 */
public class ClassChooserPanel extends javax.swing.JPanel {

    private static final Logger log = Logger.getLogger("net.sf.jaer.util");
    private static final String MISSING_DESCRIPTION_MESSAGE = "<html>No description available - provide one using @Description annotation, as in <pre>@Description(\"Example class\") \n public class MyClass</pre></html>";
    private FilterableListModel chosenClassesListModel, availClassesListModel;
    private ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> availAllList;
    private ArrayList<String> revertCopy, defaultClassNames;
    private DescriptionMap descriptionMap = new DescriptionMap();
    private Class superClass = null; // the classes available will be subclasses of this class

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

    private class AddAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            Object o = availClassJList.getSelectedValue();
            if (o == null) {
                return;
            }
            if (containsClass(chosenClassesListModel, o)) {
                boolean addIt = checkIfAddDuplicate(o);
                if (!addIt) {
                    return;
                }
            }
            int last = chosenClassesListModel.getSize() - 1;
            chosenClassesListModel.add(last + 1, o);
            classJList.setSelectedIndex(last + 1);
        }

    };

    /**
     * Creates new form ClassChooserPanel
     *
     * @param superClass a Class that will be used to search the classpath for
     * subclasses of subClassOf.
     * @param classNames a list of names, which is filled in by the actions of
     * the user with the chosen classes
     * @param defaultClassNames the list on the right is replaced by this list
     * if the user pushes the Defaults button.
     */
    public ClassChooserPanel(final Class superClass, ArrayList<String> classNames, ArrayList<String> defaultClassNames) {
        initComponents();
        this.superClass = superClass;
        includeExperimentalCB.setEnabled(onlyStableCB.isSelected());
        availFilterTextField.requestFocusInWindow();
        this.defaultClassNames = defaultClassNames;
        final DefaultListModel tmpList = new DefaultListModel();
        tmpList.addElement("scanning...");
        availClassJList.setModel(tmpList);
        try {
            availAllList = new ArrayList<ClassNameWithDescriptionAndDevelopmentStatus>();
            availClassesListModel = new FilterableListModel(availAllList);
            availClassJList.setModel(availClassesListModel);
            availClassJList.setCellRenderer(new MyCellRenderer());

            addAction(availClassJList, new AddAction());
            filterAvailable(); // user typed while list is populated
            availClassesListModel.addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent lde) {
                    filterAvailable();
                }

                @Override
                public void intervalRemoved(ListDataEvent lde) {
                }

                @Override
                public void contentsChanged(ListDataEvent lde) {
                }
            });
        } catch (Exception ex) {
            Logger.getLogger(ClassChooserPanel.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

        Action removeAction = new AbstractAction() {

            @Override
            public void actionPerformed(final ActionEvent e) {
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

        populateAvailableClassesListModel(superClass, true);

        revertCopy = new ArrayList<>(classNames);
        chosenClassesListModel = new FilterableListModel(classNames);
        classJList.setModel(chosenClassesListModel);
        classJList.setCellRenderer(new MyCellRenderer());
        descPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent event) {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String url = event.getURL().toString();
                    try {
                        Desktop.getDesktop().browse(URI.create(url));
                    } catch (IOException ex) {
                        Logger.getLogger(ClassChooserPanel.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
    }

    private void populateAvailableClassesListModel(final Class subclassOf, boolean useCache) {
        final ProgressMonitor progressMonitor = new ProgressMonitor(this, "Finding classes", "", 0, 100);
        progressMonitor.setMillisToPopup(10);
        final SubclassFinder.SubclassFinderWorker worker = new SubclassFinder.SubclassFinderWorker(subclassOf, availClassesListModel, useCache);
        worker.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
//                System.out.println(evt.getPropertyName() + "  " + evt.getNewValue());
                if ("progress" == evt.getPropertyName()) {
                    int progress = (Integer) evt.getNewValue();
                    progressMonitor.setProgress(progress);
                    String message = String.format("Completed %d%%.\n", progress);
                    progressMonitor.setNote(message);
                    if (progressMonitor.isCanceled() || worker.isDone()) {
                        if (progressMonitor.isCanceled()) {
                            worker.cancel(true);
                        }
                    }
                }
                if ((evt != null) && evt.getNewValue().equals(SwingWorker.StateValue.DONE)) {
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

                        addAction(availClassJList, new AddAction());
                        filterAvailable();
                    } catch (Exception ex) {
                        Logger.getLogger(ClassChooserPanel.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        });
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();
    }

    private boolean containsClass(FilterableListModel model, Object obj) {
        for (Object o : model.toArray()) {
            if (o instanceof String && ((String) o)
                    .equals(((ClassNameWithDescriptionAndDevelopmentStatus) obj).getClassName())) {
                return true;
            } else if (o instanceof ClassNameWithDescriptionAndDevelopmentStatus && ((ClassNameWithDescriptionAndDevelopmentStatus) o).getClassName()
                    .equals(((ClassNameWithDescriptionAndDevelopmentStatus) obj).getClassName())) {
                return true;
            }
        }
        return false;
    }

    private class ClassNameSorter implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            if ((o1 instanceof ClassNameWithDescriptionAndDevelopmentStatus) && (o2 instanceof ClassNameWithDescriptionAndDevelopmentStatus)) {
                ClassNameWithDescriptionAndDevelopmentStatus c1 = (ClassNameWithDescriptionAndDevelopmentStatus) o1;
                ClassNameWithDescriptionAndDevelopmentStatus c2 = (ClassNameWithDescriptionAndDevelopmentStatus) o2;
                return shortName(c1.getClassName()).compareTo(shortName(c2.getClassName()));
            } else {
                return -1;
            }
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

        /**
         * @param list The JList we're painting.
         * @param value The value returned by
         * list.getModel().getElementAt(index).
         * @param index The cells index.
         * @param isSelected True if the specified cell was selected.
         * @param cellHasFocus True if the specified cell has the focus.
         */
        @Override
        public Component getListCellRendererComponent(JList list, Object obj, int index, boolean isSelected, boolean cellHasFocus) {
            String fullclassname = obj.toString();
            String shortname = shortName(fullclassname);//.substring(fullclassname.lastIndexOf('.') + 1);
            setText(shortname);
            Color foreground, background;
            if (isSelected) {
                background = list.getSelectionBackground();
                DevelopmentStatus.Status develStatus = getClassDevelopmentStatus(fullclassname);
                String des = getClassDescription(fullclassname);
                ClassNameTF.setText(fullclassname);

                if (develStatus == DevelopmentStatus.Status.Experimental) {
                    foreground = Color.BLACK;
                    develStatusTF.setText(develStatus.toString());
                } else if (develStatus == DevelopmentStatus.Status.InDevelopment) {
                    foreground = Color.BLACK;
                    develStatusTF.setText(develStatus.toString());
                } else if (develStatus == DevelopmentStatus.Status.Stable) {
                    foreground = Color. BLACK;
                    develStatusTF.setText(develStatus.toString());
                } else {
                    foreground = Color.BLACK;
                    develStatusTF.setText("unknown");
                }

                if (des != null) {
                    if (des.startsWith("<html>")) {
                        setToolTipText(des);
                    } else {
                        setToolTipText(fullclassname + ": " + des);
                    }
                    descPane.setContentType("text/html");
                    descPane.setText("<html>" + shortname + ": " + des);
                } else {
                    foreground = Color.GRAY;
                    setToolTipText(fullclassname);
                    descPane.setText(MISSING_DESCRIPTION_MESSAGE);
                }
            } else {
                background = list.getBackground();
                DevelopmentStatus.Status develStatus = getClassDevelopmentStatus(fullclassname);
                if (develStatus == DevelopmentStatus.Status.Experimental) {
                    foreground = Color.ORANGE.darker();
                } else if (develStatus == DevelopmentStatus.Status.InDevelopment) {
                    foreground = Color.DARK_GRAY;
                } else if (develStatus == DevelopmentStatus.Status.Stable) {
                    foreground = Color.BLUE;
                } else {
                    foreground = Color.GRAY;
                }
                if (getClassDescription(fullclassname) == null) {
                    foreground = Color.LIGHT_GRAY;
                }
            }
            setEnabled(list.isEnabled());
            setOpaque(true);
            setForeground(foreground);
            setBackground(background);
            return this;
        }
    }

    private String shortName(String s) {
        if (s == null) {
            return "null";
        }
        int i = s.lastIndexOf('.');
        if ((i < 0) || (i == (s.length() - 1))) {
            return s;
        }
        return s.substring(i + 1);
    }

    // extends DefaultListModel to add a text filter
    public class FilterableListModel<ClassNameWithDescriptionAndDevelopmentStatus> extends DefaultListModel<ClassNameWithDescriptionAndDevelopmentStatus> {

        Vector<ClassNameWithDescriptionAndDevelopmentStatus> origList = new Vector();
        String filterString = null;

        FilterableListModel(List<ClassNameWithDescriptionAndDevelopmentStatus> list) {
            super();
            for (ClassNameWithDescriptionAndDevelopmentStatus s : list) {
                this.addElement(s);
            }
            origList.addAll(list);
        }

        synchronized void resetList() {
            clear();
            for (ClassNameWithDescriptionAndDevelopmentStatus o : origList) {
                addElement(o);
            }

        }

        synchronized void filter(String s) {
            boolean onlyStable = onlyStableCB.isSelected();
            if (((s == null) || s.equals("")) && !onlyStable) {
                resetList();
                return;
            }
            boolean passAllStable = false;
            if (onlyStable && ((s == null) || s.equals(""))) {
                passAllStable = true; // pass all stable filters 
            }

            boolean includeExperimental = includeExperimentalCB.isSelected();

            filterString = s.toLowerCase();
            resetList();

            Vector v = new Vector();  // list to prune out
            // must build a list of stuff to prune, then prune

            Enumeration en = elements();
            while (en.hasMoreElements()) { // add all elements that should be filtered out
                Object o = en.nextElement();
                net.sf.jaer.util.ClassNameWithDescriptionAndDevelopmentStatus cn;
                cn = (net.sf.jaer.util.ClassNameWithDescriptionAndDevelopmentStatus) o;
                String str = null;
                boolean isStable = false;
                DevelopmentStatus ds = cn.getDevelopmentStatus();
                if (ds != null
                        && (ds.value().equals(DevelopmentStatus.Status.Stable)
                        || (includeExperimental && ds.value().equals(DevelopmentStatus.Status.Experimental)))) {
                    isStable = true; // this class is stable
                }
                if (passAllStable) {
                    if (!isStable) {
                        v.add(o); // filter out this class because it not stable and we are passing all stable filters only
                    }
                } else { // add (filter out) depending on if strings don't match or if !isStable and onlyStable is true
                    if (onlyStable && !isStable) {
                        v.add(o);
                        continue;
                    }
                    if (includeDescriptionCB.isSelected()) {
                        str = (cn.toString() + cn.getDescription()).toLowerCase();
                    } else {
                        str = cn.toString();
                    }
                    int ind = str.indexOf(filterString);
                    if (ind == -1 || (!isStable && onlyStable)) {
                        v.add(o);
                    }
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

    public FilterableListModel getChosenClassesListModel() {
        return chosenClassesListModel;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        availClassPanel = new javax.swing.JPanel();
        availClassDesciptionPanel = new javax.swing.JScrollPane();
        availClassJList = new javax.swing.JList();
        onlyStableCB = new javax.swing.JCheckBox();
        includeExperimentalCB = new javax.swing.JCheckBox();
        filterLabel = new javax.swing.JLabel();
        availFilterTextField = new javax.swing.JTextField();
        clearFilterBut = new javax.swing.JButton();
        includeDescriptionCB = new javax.swing.JCheckBox();
        refrreshButton = new javax.swing.JButton();
        chosenClassPanel = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        classJList = new javax.swing.JList();
        descPanel = new javax.swing.JPanel();
        ClassDescSP = new javax.swing.JScrollPane();
        descPane = new javax.swing.JTextPane();
        devlStatusLbl = new javax.swing.JLabel();
        develStatusTF = new javax.swing.JTextField();
        ClassNameLbl = new javax.swing.JLabel();
        ClassNameTF = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        addClassButton = new javax.swing.JButton();
        removeClassButton = new javax.swing.JButton();
        removeAllButton = new javax.swing.JButton();
        moveUpButton = new javax.swing.JButton();
        moveDownButton = new javax.swing.JButton();
        revertButton = new javax.swing.JButton();
        defaultsButton = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(580, 686));

        availClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Available classes"));
        availClassPanel.setToolTipText("<html>If your class doesn't show up here, rebuild the project to get it into jAER.jar (or some other jar on the classpath). <p> Yyour class must be concrete (not abstract). <p> Finally, if your class lives in a separate JAR archive, make sure your archive classpath is not on the excluded list in the class ListClasses.");
        availClassPanel.setPreferredSize(new java.awt.Dimension(400, 300));

        availClassDesciptionPanel.setBorder(null);

        availClassJList.setToolTipText("If your class doesn't show up here, rebuild the project to get it into jAER.jar (or some other jar on the classpath) and hit the Refresh button to rescan classpath.");
        availClassDesciptionPanel.setViewportView(availClassJList);
        availClassJList.getAccessibleContext().setAccessibleDescription("");

        onlyStableCB.setText("Only Stable");
        onlyStableCB.setToolTipText("Show only items with DevelopmentStatus.Stable");
        onlyStableCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onlyStableCBActionPerformed(evt);
            }
        });

        includeExperimentalCB.setText("Include Experimental");
        includeExperimentalCB.setToolTipText("Include Experimental classes in search results");
        includeExperimentalCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                includeExperimentalCBActionPerformed(evt);
            }
        });

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

        includeDescriptionCB.setSelected(true);
        includeDescriptionCB.setText("Include descriptions");
        includeDescriptionCB.setToolTipText("Include class Descriptions in filter results");
        includeDescriptionCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                includeDescriptionCBActionPerformed(evt);
            }
        });

        refrreshButton.setText("Refresh list");
        refrreshButton.setToolTipText("<html> <p>Scans entire classpath for possible classes</p><p>(needs to be refreshed if new classes are added)</p>");
        refrreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refrreshButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout availClassPanelLayout = new javax.swing.GroupLayout(availClassPanel);
        availClassPanel.setLayout(availClassPanelLayout);
        availClassPanelLayout.setHorizontalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availClassPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(availClassDesciptionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(includeExperimentalCB)
                    .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(availClassPanelLayout.createSequentialGroup()
                            .addComponent(onlyStableCB)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(includeDescriptionCB))
                        .addGroup(availClassPanelLayout.createSequentialGroup()
                            .addComponent(filterLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(availFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(clearFilterBut)
                            .addGap(44, 44, 44)))))
            .addComponent(refrreshButton)
        );
        availClassPanelLayout.setVerticalGroup(
            availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, availClassPanelLayout.createSequentialGroup()
                .addComponent(refrreshButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filterLabel)
                    .addComponent(availFilterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(clearFilterBut))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(availClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(onlyStableCB)
                    .addComponent(includeDescriptionCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(includeExperimentalCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(availClassDesciptionPanel)
                .addContainerGap())
        );

        chosenClassPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Selected classes"));
        chosenClassPanel.setToolTipText("These classes will be available to choose.");
        chosenClassPanel.setPreferredSize(new java.awt.Dimension(400, 300));

        jScrollPane3.setBorder(null);

        classJList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        classJList.setToolTipText("These classes will be available to choose.");
        classJList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                classJListMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(classJList);
        classJList.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout chosenClassPanelLayout = new javax.swing.GroupLayout(chosenClassPanel);
        chosenClassPanel.setLayout(chosenClassPanelLayout);
        chosenClassPanelLayout.setHorizontalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        chosenClassPanelLayout.setVerticalGroup(
            chosenClassPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane3)
        );

        descPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Class description"));

        ClassDescSP.setBorder(null);

        descPane.setEditable(false);
        descPane.setBorder(null);
        ClassDescSP.setViewportView(descPane);

        devlStatusLbl.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        devlStatusLbl.setText("Development status:");

        develStatusTF.setEditable(false);
        develStatusTF.setBackground(new java.awt.Color(255, 255, 255));
        develStatusTF.setToolTipText("Shows DevelopmentStatus of class as annotated with DevelopmentStatus");
        develStatusTF.setBorder(null);

        ClassNameLbl.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        ClassNameLbl.setText("Full class name:");

        ClassNameTF.setEditable(false);
        ClassNameTF.setBackground(new java.awt.Color(255, 255, 255));
        ClassNameTF.setToolTipText("Shows the full classname of a class and hence its location in the jAER project");
        ClassNameTF.setBorder(null);

        javax.swing.GroupLayout descPanelLayout = new javax.swing.GroupLayout(descPanel);
        descPanel.setLayout(descPanelLayout);
        descPanelLayout.setHorizontalGroup(
            descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(descPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(descPanelLayout.createSequentialGroup()
                        .addComponent(ClassDescSP, javax.swing.GroupLayout.DEFAULT_SIZE, 642, Short.MAX_VALUE)
                        .addGap(2, 2, 2))
                    .addGroup(descPanelLayout.createSequentialGroup()
                        .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(devlStatusLbl)
                            .addComponent(ClassNameLbl, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ClassNameTF, javax.swing.GroupLayout.DEFAULT_SIZE, 442, Short.MAX_VALUE)
                            .addComponent(develStatusTF, javax.swing.GroupLayout.DEFAULT_SIZE, 442, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        descPanelLayout.setVerticalGroup(
            descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(descPanelLayout.createSequentialGroup()
                .addComponent(ClassDescSP, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(devlStatusLbl)
                    .addComponent(develStatusTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addGroup(descPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ClassNameLbl)
                    .addComponent(ClassNameTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        addClassButton.setMnemonic('a');
        addClassButton.setText(">");
        addClassButton.setToolTipText("Add the filter to the list of available filters");
        addClassButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        addClassButton.setMaximumSize(null);
        addClassButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addClassButtonActionPerformed(evt);
            }
        });

        removeClassButton.setMnemonic('r');
        removeClassButton.setText("<");
        removeClassButton.setToolTipText("Remove the filter from the list of selected filters");
        removeClassButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        removeClassButton.setMaximumSize(null);
        removeClassButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeClassButtonActionPerformed(evt);
            }
        });

        removeAllButton.setText("<<");
        removeAllButton.setToolTipText("Remove all filters from the list of selected filters");
        removeAllButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        removeAllButton.setMaximumSize(null);
        removeAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAllButtonActionPerformed(evt);
            }
        });

        moveUpButton.setMnemonic('u');
        moveUpButton.setText("Move up");
        moveUpButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        moveUpButton.setMaximumSize(null);
        moveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveUpButtonActionPerformed(evt);
            }
        });

        moveDownButton.setMnemonic('d');
        moveDownButton.setText("Move down");
        moveDownButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        moveDownButton.setMaximumSize(null);
        moveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveDownButtonActionPerformed(evt);
            }
        });

        revertButton.setMnemonic('e');
        revertButton.setText("Revert");
        revertButton.setToolTipText("Revert changes to the list");
        revertButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        revertButton.setMaximumSize(null);
        revertButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                revertButtonActionPerformed(evt);
            }
        });

        defaultsButton.setMnemonic('d');
        defaultsButton.setText("Add Defaults");
        defaultsButton.setToolTipText("Adds the defaults to the end of the selected classes list");
        defaultsButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        defaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(defaultsButton, javax.swing.GroupLayout.DEFAULT_SIZE, 116, Short.MAX_VALUE)
                    .addComponent(removeClassButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(removeAllButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(moveUpButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(revertButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(moveDownButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(addClassButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(addClassButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeClassButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeAllButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(moveUpButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveDownButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(revertButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(defaultsButton))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(descPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(availClassPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chosenClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 178, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(chosenClassPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 563, Short.MAX_VALUE)
                            .addComponent(availClassPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 563, Short.MAX_VALUE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(40, 40, 40)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 135, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(descPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void classJListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_classJListMouseClicked
        moveDownButton.setEnabled(true);
        moveUpButton.setEnabled(true);
    }//GEN-LAST:event_classJListMouseClicked

    private void clearFilterButActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearFilterButActionPerformed
        availFilterTextField.setText("");
        availClassesListModel.clearFilter();
    }//GEN-LAST:event_clearFilterButActionPerformed

    private void availFilterTextFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_availFilterTextFieldKeyTyped
        filterAvailable();
    }//GEN-LAST:event_availFilterTextFieldKeyTyped

    private void availFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_availFilterTextFieldActionPerformed
        filterAvailable();
    }//GEN-LAST:event_availFilterTextFieldActionPerformed

    private void filterAvailable() {
        if (availClassesListModel == null) {
            return;
        }
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }

    private void includeDescriptionCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_includeDescriptionCBActionPerformed
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_includeDescriptionCBActionPerformed

    private void onlyStableCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onlyStableCBActionPerformed
        if (availClassesListModel == null) {
            return;  // not ready yet
        }
        includeExperimentalCB.setEnabled(onlyStableCB.isSelected());
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_onlyStableCBActionPerformed

    private void includeExperimentalCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_includeExperimentalCBActionPerformed
        String s = availFilterTextField.getText();
        availClassesListModel.filter(s);
    }//GEN-LAST:event_includeExperimentalCBActionPerformed

    private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsButtonActionPerformed
        //        chosenClassesListModel.clear();
        int i = 0;
        if (defaultClassNames == null) {
            log.warning("No default classes to add");
            return;
        }
        for (String s : defaultClassNames) { // add them in reverse order because they were added to the list
            chosenClassesListModel.insertElementAt(s, i++);
            //            chosenClassesListModel.addElement(s);
        }
    }//GEN-LAST:event_defaultsButtonActionPerformed

    private void revertButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_revertButtonActionPerformed
        chosenClassesListModel.clear();
        for (String s : revertCopy) {
            chosenClassesListModel.addElement(s);
        }
    }//GEN-LAST:event_revertButtonActionPerformed

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

    private void removeAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAllButtonActionPerformed
        chosenClassesListModel.clear();
    }//GEN-LAST:event_removeAllButtonActionPerformed

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

    private void addClassButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addClassButtonActionPerformed
        Object o = availClassJList.getSelectedValue();
        if (o == null) {
            return;
        }
        if (containsClass(chosenClassesListModel, o)) {
            boolean addIt = checkIfAddDuplicate(o);
            if (!addIt) {
                return;
            }
        }
        int last = chosenClassesListModel.getSize() - 1;
        chosenClassesListModel.add(last + 1, o);
        classJList.setSelectedIndex(last + 1);
    }//GEN-LAST:event_addClassButtonActionPerformed

    private void refrreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refrreshButtonActionPerformed
        populateAvailableClassesListModel(superClass, false);
    }//GEN-LAST:event_refrreshButtonActionPerformed

    private boolean checkIfAddDuplicate(Object o) {
        int retVal = JOptionPane.showOptionDialog(this,
                "List already contains " + o.toString() + "; add it anyway?", "Confirm addition",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{"Yes", "No"}, "Yes");
        return retVal == JOptionPane.YES_OPTION;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane ClassDescSP;
    private javax.swing.JLabel ClassNameLbl;
    private javax.swing.JTextField ClassNameTF;
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
    private javax.swing.JLabel devlStatusLbl;
    private javax.swing.JLabel filterLabel;
    private javax.swing.JCheckBox includeDescriptionCB;
    private javax.swing.JCheckBox includeExperimentalCB;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton moveDownButton;
    private javax.swing.JButton moveUpButton;
    private javax.swing.JCheckBox onlyStableCB;
    private javax.swing.JButton refrreshButton;
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

        @Override
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
