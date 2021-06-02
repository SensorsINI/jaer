/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;


import ch.unizh.ini.jaer.projects.apsdvsfusion.FiringModelMap;

/**
 * @author Dennis
 *
 */
public class ContinuousOutputViewerManager {

	ArrayList<ContinuousOutputViewer> viewers = new ArrayList<ContinuousOutputViewer>();
    
//	JPanel statePanel=new JPanel();
//    JPanel myPanel;
//    Component controlPanel;
 //   JTextComponent textComponent;
    Thread viewLoop;
    public volatile int updateMicros=30000;           // Update interval, in milliseconds
    private volatile boolean enable=true;
//    private JFrame displayFrame = null;
    
    
    
	/**
	 * 
	 */
	public ContinuousOutputViewerManager() {
	}
     
	public void setUpdateMicros(int micros) {
		if (micros != updateMicros) {
			this.updateMicros = micros;
		}
		
	}
    
    public SpikingOutputViewer createOutputViewer(FiringModelMap map, int grayLevels) {
    	SpikingOutputViewer soViewer = new SpikingOutputViewer(map, grayLevels);
    	addOutputViewer(soViewer);
    	
//    	JPanel displayPanel=new JPanel();
//        
//        displayPanel.setBackground(Color.darkGray);
//        displayPanel.setLayout(new BorderLayout());
//        
//        displayPanel.add(soViewer.getDisplay(),BorderLayout.CENTER);  
//        statePanel.add(displayPanel);
//        statePanel.validate();

        return soViewer;
    }
    
    public SpikingOutputViewer createOutputViewer(int sizeX, int sizeY, int grayLevels) {
    	SpikingOutputViewer soViewer = new SpikingOutputViewer(sizeX, sizeY, grayLevels);
    	addOutputViewer(soViewer);
    	
//    	JPanel displayPanel=new JPanel();
//        
//        displayPanel.setBackground(Color.darkGray);
//        displayPanel.setLayout(new BorderLayout());
//        
//        displayPanel.add(soViewer.getDisplay(),BorderLayout.CENTER);  
//        statePanel.add(displayPanel);
//        statePanel.validate();

        return soViewer;
    }
    
    public void addOutputViewer(ContinuousOutputViewer soViewer) {
    	synchronized (viewers) {
    		if (!viewers.contains(soViewer))
    			this.viewers.add(soViewer);
		}
    }
    
    public void removeOutputViewer(ContinuousOutputViewer soViewer) {
    	synchronized (viewers) {
    		if (viewers.contains(soViewer))
    			viewers.remove(soViewer);
		}
    }
    
//    public void addControls(ControlPanel cp)
//    {  
//        controlPanel=cp;
//    }
    
    public void run() {
		if (viewLoop == null || !viewLoop.isAlive())
			runViewLoop();
    }
    
    public void stop() {
		if (viewLoop != null && viewLoop.isAlive())
			kill();
    }
    
//    public void setVisible(boolean visible) {
//    	if (visible) {
//    		if (displayFrame == null)
//    			createFrame();
//    		else 
//    			displayFrame.setVisible(true);
//    		if (viewLoop == null || !viewLoop.isAlive())
//    			runViewLoop();
//    	}
//    	else {
//    		if (displayFrame != null)
//    			displayFrame.setVisible(false);
//    		if (viewLoop != null && viewLoop.isAlive())
//    			kill();
//    	}
//    }
    
//    protected void createFrame()   {   
//        SwingUtilities.invokeLater(new Runnable()
//        {
//            @Override
//            public void run() {
//            	synchronized (SpikingOutputViewerManager.this) {
//            		displayFrame=new JFrame();
//            	}
//            	fillFrame();
//            }
//            
//        });
//    }

//    /** Create a figure for plotting the state of the network for the network */
//    protected void addFrameElements(final JPanel hostPanel)
//    {
//
//        statePanel.setBackground(Color.BLACK);
//        statePanel.setLayout(new FlowLayout());
//        
//        textComponent=new JTextArea();
//        textComponent.setBackground(Color.BLACK);
//        textComponent.setForeground(Color.white);
//        textComponent.setEditable(false);
//        textComponent.setAlignmentY(.5f);
//        textComponent.setPreferredSize(new Dimension(400,100));
//
//        JPanel textComponentPanel=new JPanel();
//        textComponentPanel.setBackground(Color.black);
//        textComponentPanel.add(textComponent);
//        
//        hostPanel.add(textComponentPanel,BorderLayout.SOUTH);
//        
//        statePanel.setVisible(true);
//        statePanel.repaint();
//
//        hostPanel.add(statePanel,BorderLayout.CENTER);
//        
//        // If controls have been added, create control panel.
//        if (controlPanel!=null)
//        {   
//            JScrollPane jsp=new JScrollPane(controlPanel,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
//            jsp.setPreferredSize(new Dimension(controlPanel.getPreferredSize().width,800));
//            hostPanel.add(jsp,BorderLayout.WEST);
//        }
//    }
    
    
//    private void fillFrame() {
//        
//        myPanel = new JPanel();
//        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
//		myPanel.setPreferredSize(new Dimension((int)screenSize.getWidth()/2, (int)screenSize.getHeight()/2));
//        myPanel.setLayout(new BorderLayout());
// 
//        if (!SwingUtilities.isEventDispatchThread())
//        {  
//            try {
//                SwingUtilities.invokeAndWait(new Runnable(){
//                    @Override
//                    public void run() {
//                        addFrameElements(myPanel);
//                    }
//                });
//            } catch (InterruptedException ex) {
//                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
//            } catch (InvocationTargetException ex) {
//                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
//            }
//        }
//        else
//            addFrameElements(myPanel);
//        
////        mainPanel.add(myPanel);
////        myPanel.add(myPanel);
//        
//        
//        myPanel.addComponentListener(new ComponentListener() {
////        mainPanel.addComponentListener(new ComponentListener() {
//            /* TODO: Ponder about whether this is the best solution */
//			@Override
//			public void componentShown(ComponentEvent arg0) {		}
//			
//			@Override
//			public void componentResized(ComponentEvent arg0) {		}
//			
//			@Override
//			public void componentMoved(ComponentEvent arg0) {		}
//			
//			@Override
//            public void componentHidden(ComponentEvent e) {
//                enable=false; 
//            }
//		});
////        runViewLoop();
//
//        displayFrame.getContentPane().setBackground(Color.GRAY);
//        displayFrame.setLayout(new GridBagLayout());
//        displayFrame.setContentPane(myPanel);
////        displayFrame.setContentPane(mainPanel);
//        
//        displayFrame.pack();
//        displayFrame.setVisible(true);   
//    }
    
    
//    /** Create a plot of the network state and launch a thread that updates this plot.*/
//    private void runDisplays(final Container mainPanel)
//    {
//    }
    
    private void runViewLoop() {
        if (viewLoop!=null && viewLoop.isAlive())
            throw new RuntimeException("You're trying to start the View Loop, but it's already running");
    	viewLoop = new Thread("ContinuousOutputDisplay") {
            @Override
            public void run()
            {
            	while (enable) {
            		updateSingleViewers();
            		try {
            			Thread.sleep(updateMicros/1000);
            		} catch (InterruptedException ex) {
            			break;
            			//                            Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
            		}
            	}
            	synchronized(ContinuousOutputViewerManager.this)
            	{
            		ContinuousOutputViewerManager.this.notify();
            	}
            }
        };
        viewLoop.start();
    }
    
    protected void updateSingleViewers() {
    	synchronized (viewers) {
        	for (ContinuousOutputViewer sov : viewers) {
        		sov.updateOutput();
    		}
		}
    }
        
    public void kill()
    {
        if (viewLoop!=null && viewLoop.isAlive())
        {
            synchronized(this)
            {
                viewLoop.interrupt();
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                }
                System.out.println("Display Terminated");
            }
        }
    }
    
    public void reset()
    {
        synchronized(this)
        {
        	kill();
        	synchronized (viewers) {
            	viewers.clear();
			}
        	
//        	statePanel.removeAll();
        	
//        	fillFrame();

        	runViewLoop();
        }
    }

}
