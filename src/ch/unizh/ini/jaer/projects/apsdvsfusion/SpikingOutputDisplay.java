/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import jspikestack.ControlPanel;
import jspikestack.ImageDisplay;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class SpikingOutputDisplay {

	ArrayList<SingleOutputViewer> viewers = new ArrayList<SpikingOutputDisplay.SingleOutputViewer>();
    
	JPanel statePanel=new JPanel();
    JPanel myPanel;
    Component controlPanel;
    JTextComponent textComponent;
    Thread viewLoop;
    public int updateMicros=30000;           // Update interval, in milliseconds
    private volatile boolean enable=true;
    
    public class SingleOutputViewer implements SpikeHandler {   
    	int sizeX = 0, sizeY = 0;
        ImageDisplay display ;
        int maxValueInBuffer = 0;
        float[] state;  // Array of unit states.
    	public int[][] receivedSpikes;
    	public int[][] receivedSpikesBuffer;
        
        public SingleOutputViewer(int sizeX, int sizeY)    {
        	changeSize(sizeX, sizeY);
            display = ImageDisplay.createOpenGLCanvas();
            display.setSizeX(sizeX);
            display.setSizeY(sizeY);
            display.setPreferredSize(new Dimension(400,400));
            display.setBorderSpacePixels(18);
            this.display.setFontSize(14);
        }
        
        public void changeSize(int sizeX, int sizeY) {
        	if (sizeX != this.sizeX || sizeY != this.sizeY) {
            	this.sizeX = sizeX;
            	this.sizeY = sizeY;
            	this.receivedSpikes = new int[sizeX][sizeY];
            	this.receivedSpikesBuffer = new int[sizeX][sizeY];
        	}
        	
        }

        /* Update layerStatePlots to current network time */
        public void update()  {
        	// swap buffer and receivedSpikes:
        	synchronized (this.receivedSpikes) {
        		synchronized (this.receivedSpikesBuffer) {
                	this.maxValueInBuffer = 1; 
//        			int[][] dummy = this.receivedSpikesBuffer;
//        			this.receivedSpikesBuffer = this.receivedSpikes;
//        			this.receivedSpikes = dummy;
        			for (int i = 0; i < receivedSpikes.length; i++) {
						for (int j = 0; j < receivedSpikes[i].length; j++) {
							receivedSpikesBuffer[i][j] += receivedSpikes[i][j];
							receivedSpikes[i][j] = 0;
						}
					}
        		}
        	}
            SwingUtilities.invokeLater(new Runnable(){
                    @Override
                    public void run() {
                        synchronized (receivedSpikesBuffer) {
                        	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
                        		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
                        			int value = receivedSpikesBuffer[x][y];
                        			receivedSpikesBuffer[x][y] = 0;
                        			if (value > maxValueInBuffer) value = maxValueInBuffer;
                        			display.setPixmapGray(x, y, (float)value / (float)maxValueInBuffer);
                        		}
                        	}
                        }
// TODO: title!                        display.setTitleLabel(layer.getName()+" "+stateTrackers[iimax].getLabel(ssmin,ssmax));
                        display.repaint();
                    }
            });
        }

		@Override
		public void spikeAt(int x, int y, int time, Polarity polarity) {
			synchronized (receivedSpikes) {
				receivedSpikes[x][y]++;
			}
		}

		public void reset() {
            synchronized (receivedSpikesBuffer) {
            	for (int x = 0; x < receivedSpikesBuffer.length; x++) {
            		for (int y = 0; y < receivedSpikesBuffer[x].length; y++) {
            			receivedSpikes[x][y] = 0;
            			receivedSpikesBuffer[x][y] = 0;
            		}
            	}
            }
		}

		public ImageDisplay getDisplay() {
			return display;
		}
    }
	
	/**
	 * 
	 */
	public SpikingOutputDisplay() {
	}
    
    public JPanel setupGUI()
    {
        final JPanel hostPanel=new JPanel();
        hostPanel.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
        hostPanel.setLayout(new BorderLayout());
 
        if (!SwingUtilities.isEventDispatchThread())
        {  
            try {
                SwingUtilities.invokeAndWait(new Runnable(){
                    @Override
                    public void run() {
                        addFrameElements(hostPanel);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            }
        }
        else
            addFrameElements(hostPanel);
        return hostPanel;
    }
   
     
    /** Create a figure for plotting the state of the network for the network */
    protected void addFrameElements(final JPanel hostPanel)
    {

        statePanel.setBackground(Color.BLACK);
        statePanel.setLayout(new FlowLayout());
        
        textComponent=new JTextArea();
        textComponent.setBackground(Color.BLACK);
        textComponent.setForeground(Color.white);
        textComponent.setEditable(false);
        textComponent.setAlignmentY(.5f);
        textComponent.setPreferredSize(new Dimension(400,100));

        JPanel textComponentPanel=new JPanel();
        textComponentPanel.setBackground(Color.black);
        textComponentPanel.add(textComponent);
        
        hostPanel.add(textComponentPanel,BorderLayout.SOUTH);
        
        statePanel.setVisible(true);
        statePanel.repaint();

        hostPanel.add(statePanel,BorderLayout.CENTER);
        
        // If controls have been added, create control panel.
        if (controlPanel!=null)
        {   
            JScrollPane jsp=new JScrollPane(controlPanel,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            jsp.setPreferredSize(new Dimension(controlPanel.getPreferredSize().width,800));
            hostPanel.add(jsp,BorderLayout.WEST);
        }
    }
    
    
    public SingleOutputViewer createOutputViewer(int sizeX, int sizeY) {
    	SingleOutputViewer soViewer = new SingleOutputViewer(sizeX, sizeY);
    	this.viewers.add(soViewer);
        JPanel displayPanel=new JPanel();
        
        displayPanel.setBackground(Color.darkGray);
        displayPanel.setLayout(new BorderLayout());
        
        
//        GridBagConstraints c = new GridBagConstraints();
//        c.fill=GridBagConstraints.HORIZONTAL;
//        c.gridx=viewers.size() % 5;
//        c.gridy=viewers.size() / 5;
//        c.gridheight=2;
       
        displayPanel.add(soViewer.getDisplay(),BorderLayout.CENTER);  
        statePanel.add(displayPanel);
        statePanel.validate();
    	return soViewer;
    }
    
    public void addControls(ControlPanel cp)
    {  
        controlPanel=cp;
    }
    
    public void runDisplays()
    {   
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run() {
                JFrame frame=new JFrame();
                
                JPanel mainPanel=new JPanel();
                               
                
                runDisplays(mainPanel);   

                frame.getContentPane().setBackground(Color.GRAY);
                frame.setLayout(new GridBagLayout());
                frame.setContentPane(mainPanel);
                
                

                frame.pack();
                frame.setVisible(true);   
            }
            
        });
        
        
    }
    
    
    /** Create a plot of the network state and launch a thread that updates this plot.*/
    private void runDisplays(final Container mainPanel)
    {
        myPanel=setupGUI();
        mainPanel.add(myPanel);
        
        if (viewLoop!=null && viewLoop.isAlive())
            throw new RuntimeException("You're trying to start the View Loop, but it's already running");
        
                
        class ViewLoop extends Thread{
            
            public ViewLoop() {
                super();
                setName("SpikingOutputDisplay");
            }
            
            /* TODO: Ponder about whether this is the best solution */
            @Override
            public void run()
            {
                 mainPanel.addComponentListener(new ComponentListener(){
                    @Override
                    public void componentResized(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentMoved(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentShown(ComponentEvent e) {
//                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void componentHidden(ComponentEvent e) {
                        enable=false; 
                    }
                });
                
                 while (enable) {
                	 updateSingleViewers();
                	 try {
                		 Thread.sleep(updateMicros/1000);
                	 } catch (InterruptedException ex) {
                		 break;
                		 //                            Logger.getLogger(NetPlotter.class.getName()).log(Level.SEVERE, null, ex);
                	 }
                 }
                synchronized(SpikingOutputDisplay.this)
                {
                    SpikingOutputDisplay.this.notify();
                }
            }
        }
        
        viewLoop=new ViewLoop();
        viewLoop.start();
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
        	for (SingleOutputViewer soViewer : viewers) {
        		soViewer.reset();
        	}
        }
    }
    public void updateSingleViewers() {
    	for (SingleOutputViewer sov : viewers) {
    		sov.update();
		}
    }
}
