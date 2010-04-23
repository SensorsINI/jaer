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

    public static final String clearButtonName = "Clear Trajectory";
    public static final int PANEL_SIZE = 500;
    protected ArrayList<Point2D.Float> trajectory = new ArrayList<Point2D.Float>();

    Image img = null;
    Graphics gImg = null;

    public TrajectoryDrawingPanel(String title) {
        super(title);
    }
    /**
     * Constructor
     * @param title : drawing panel frame title
     * @param componentNames : list of names for buttons used to control the panel
     */
    public TrajectoryDrawingPanel(String title, String[] componentNames) {
        this(title);

        initLayout(componentNames);
        
        // add event listeners
        addWindowListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        setVisible(true);

        img = createImage(PANEL_SIZE, PANEL_SIZE);
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

        setBounds(100, 100, PANEL_SIZE+25, PANEL_SIZE+25);
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
        gImg.drawString("Draw a trajectory using the mouse with teh left button pressed", 10, 50);
        gImg.drawString("X", 65, PANEL_SIZE - 5);
        gImg.drawString("Y", 8, PANEL_SIZE - 65);
        gImg.drawString("(0, 0)", 15, PANEL_SIZE - 15);
        gImg.drawLine(10, PANEL_SIZE - 10, 60, PANEL_SIZE - 10);
        gImg.drawLine(55, PANEL_SIZE - 15, 60, PANEL_SIZE - 10);
        gImg.drawLine(55, PANEL_SIZE - 5, 60, PANEL_SIZE - 10);
        gImg.drawLine(10, PANEL_SIZE - 10, 10, PANEL_SIZE - 60);
        gImg.drawLine(5, PANEL_SIZE - 55, 10, PANEL_SIZE - 60);
        gImg.drawLine(15, PANEL_SIZE - 55, 10, PANEL_SIZE - 60);
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
            newPoint.y = PANEL_SIZE - me.getY();
            trajectory.add(newPoint);

            gImg.drawOval((int) newPoint.x - 3, PANEL_SIZE - (int) newPoint.y - 3, 6, 6);
            if(trajectory.size() > 1){
                Point2D.Float prevPoint = trajectory.get(trajectory.size()-2);
                gImg.drawLine((int) prevPoint.x, PANEL_SIZE - (int) prevPoint.y, (int) newPoint.x, PANEL_SIZE - (int) newPoint.y);
            }
            repaint();
        }
    }

    /**
     * mouse listener
     */
    public void mouseClicked(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1){
            Point2D.Float newPoint = new Point2D.Float();
            newPoint.x = e.getX();
            newPoint.y = PANEL_SIZE - e.getY();
            trajectory.add(newPoint);

            gImg.drawOval((int) newPoint.x - 3, PANEL_SIZE - (int) newPoint.y - 3, 6, 6);
            if(trajectory.size() > 1){
                Point2D.Float prevPoint = trajectory.get(trajectory.size()-2);
                gImg.drawLine((int) prevPoint.x, PANEL_SIZE - (int) prevPoint.y, (int) newPoint.x, PANEL_SIZE - (int) newPoint.y);
            }
            repaint();
        }
    }

    /**
     * mouse listener
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * mouse listener
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * mouse listener
     */
    public void mousePressed(MouseEvent e) {

    }

    /**
     * mouse listener
     */
    public void mouseReleased(MouseEvent e) {

    }


    /**
     * action listener
     */
    public void actionPerformed(ActionEvent e) {
        String buttonName = e.getActionCommand();

        if(buttonName.equals(clearButtonName)) {
            clearImage();
        } else {
            buttonAction(buttonName);
        }
    }

    public void clearImage(){
        trajectory.clear();

        gImg.clearRect(0, 0, 500, 500);
        initialDeco();
        repaint();
    }

    /**
     * This funtions has to be implemented to define the action followed by a button click
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
     * @param e
     */
    public void windowClosed(WindowEvent we) {

    }

    /**
     * window listener
     * @param e
     */
    public void windowClosing(WindowEvent we) {
        System.exit(0);
    }
}
