/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
/**
 * Abstract class for trajectory drawing panel. Feature vector extraction and gesture simulation can be implemented based on this class.
 * @author Jun Haeng Lee
 */
abstract public class TrajectoryDrawingPanel extends Frame implements MouseMotionListener, MouseListener, ActionListener, WindowListener{

    /**
     * Name for button 'Clear trajectory' which is a default button always added to the menu. 
     */
    public static final String clearButtonName = "Clear Trajectory";

    /**
     * Width and height of image panel to draw a trajectory
     */
    public int imgPanelWidth, imgPanelHeight;

    /**
     * Array for a trajectory
     */
    protected ArrayList<Point2D.Float> trajectory = new ArrayList<Point2D.Float>();

    Image img = null;
    Graphics gImg = null;

    /**
     * Constructor with the title and the size of image panel
     * @param title
     * @param imgPanelWidth
     * @param imgPanelHeight
     */
    public TrajectoryDrawingPanel(String title, int imgPanelWidth, int imgPanelHeight) {
        super(title);
        this.imgPanelWidth = imgPanelWidth;
        this.imgPanelHeight = imgPanelHeight;
    }
    /**
     * Constructor with the title, the size of image panel, and component names for menu
     * @param title : drawing panel frame title
     * @param imgPanelWidth 
     * @param componentNames : list of names for buttons used to control the panel
     * @param imgPanelHeight
     */
    public TrajectoryDrawingPanel(String title, int imgPanelWidth, int imgPanelHeight, String[] componentNames) {
        this(title, imgPanelWidth, imgPanelHeight);

        initLayout(componentNames);
        
        // add event listeners
        addWindowListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        setVisible(true);

        img = createImage(imgPanelWidth, imgPanelHeight);
        gImg = img.getGraphics();
        initialDeco();
        repaint();
    }

    /** override this method to change the layout
     *
     * @param componentNames
     */
    public void initLayout(String[] componentNames){
        setLayout(new BorderLayout());

        // configuration of button panel
        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new GridLayout(1, componentNames.length));
        for(int i = 1; i<= componentNames.length; i++){
            Button newButton = new Button(componentNames[i-1]);
            buttonPanel.add(newButton, ""+i);
            newButton.addActionListener(this);
        }
        Button clearButton = new Button(clearButtonName);
        buttonPanel.add(clearButton, ""+componentNames.length);
        clearButton.addActionListener(this);
        add(buttonPanel, "South");

        setBounds(100, 100, imgPanelWidth, imgPanelHeight+25);
        setResizable(false);
    }

    @Override
    public void paint(Graphics g) {
        if(img != null)
            g.drawImage(img, 0, 0, this);
    }

    /**
     * Initial decorations
     */
    private void initialDeco(){
        gImg.drawString("Draw a trajectory by dragging your mouse.", 10, 50);
        gImg.drawString("X", 65, imgPanelHeight - 5);
        gImg.drawString("Y", 8, imgPanelHeight - 65);
        gImg.drawString("(0, 0)", 15, imgPanelHeight - 15);
        gImg.drawLine(10, imgPanelWidth - 10, 60, imgPanelHeight - 10);
        gImg.drawLine(55, imgPanelWidth - 15, 60, imgPanelHeight - 10);
        gImg.drawLine(55, imgPanelWidth - 5, 60, imgPanelHeight - 10);
        gImg.drawLine(10, imgPanelWidth - 10, 10, imgPanelHeight - 60);
        gImg.drawLine(5, imgPanelWidth - 55, 10, imgPanelHeight - 60);
        gImg.drawLine(15, imgPanelWidth - 55, 10, imgPanelHeight - 60);
    }

    /**
     * mouse motion listener
     * @param me
     */
    public void mouseMoved(MouseEvent me){
        
    }

    /**
     * mouse motion listener
     * @param me
     */
    public void mouseDragged(MouseEvent me){
        if(me.getModifiersEx() == MouseEvent.BUTTON1_DOWN_MASK){
            Point2D.Float newPoint = new Point2D.Float();
            newPoint.x = me.getX();
            newPoint.y = imgPanelHeight - me.getY();
            trajectory.add(newPoint);

            gImg.drawOval((int) newPoint.x - 3, imgPanelHeight - (int) newPoint.y - 3, 6, 6);
            if(trajectory.size() > 1){
                Point2D.Float prevPoint = trajectory.get(trajectory.size()-2);
                gImg.drawLine((int) prevPoint.x, imgPanelHeight - (int) prevPoint.y, (int) newPoint.x, imgPanelHeight - (int) newPoint.y);
            }
            repaint();
        }
    }

    /**
     * mouse listener
     * @param e
     */
    public void mouseClicked(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1){
            Point2D.Float newPoint = new Point2D.Float();
            newPoint.x = e.getX();
            newPoint.y = imgPanelHeight - e.getY();
            trajectory.add(newPoint);

            gImg.drawOval((int) newPoint.x - 3, imgPanelHeight - (int) newPoint.y - 3, 6, 6);
            if(trajectory.size() > 1){
                Point2D.Float prevPoint = trajectory.get(trajectory.size()-2);
                gImg.drawLine((int) prevPoint.x, imgPanelHeight - (int) prevPoint.y, (int) newPoint.x, imgPanelHeight - (int) newPoint.y);
            }
            repaint();
        }
    }

    /**
     * mouse listener
     * @param e
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * mouse listener
     * @param e
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * mouse listener
     * @param e
     */
    public void mousePressed(MouseEvent e) {

    }

    /**
     * mouse listener
     * @param e
     */
    public void mouseReleased(MouseEvent e) {

    }


    /**
     * action listener
     * @param e
     */
    public void actionPerformed(ActionEvent e) {
        String buttonName = e.getActionCommand();

        if(buttonName.equals(clearButtonName)) {
            clearImage();
        } else {
            buttonAction(buttonName);
        }
    }

    /** Clear images drawn on the image panel
     *
     */
    public void clearImage(){
        trajectory.clear();
        gImg.clearRect(0, 0, imgPanelWidth, imgPanelHeight);
        initialDeco();
        repaint();
    }

    /**
     * This funtions has to be implemented to define the action followed by a button click on the menu
     * Names of buttons are defined as componenetNames in Constructor
     * @param buttonName
     */
    abstract public void buttonAction(String buttonName);


    /**
     * window listener
     * @param e
     */
    public void windowActivated(WindowEvent e) {

    }

    /**
     * window listener
     * @param e
     */
    public void windowDeactivated(WindowEvent e) {

    }

    /**
     * window listener
     * @param e
     */
    public void windowDeiconified(WindowEvent e) {

    }

    /**
     * window listener
     * @param e
     */
    public void windowIconified(WindowEvent e) {

    }

    /**
     * window listener
     * @param e
     */
    public void windowOpened(WindowEvent e) {

    }

    /**
     * window listener
     * @param we
     */
    public void windowClosed(WindowEvent we) {

    }

    /**
     * window listener
     * @param we
     */
    public void windowClosing(WindowEvent we) {
        System.exit(0);
    }
}
