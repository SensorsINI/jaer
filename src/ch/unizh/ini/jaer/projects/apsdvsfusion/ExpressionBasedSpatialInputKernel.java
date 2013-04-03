/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;

import net.sf.jaer.event.PolarityEvent.Polarity;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.CollapsablePanel;
import ch.unizh.ini.jaer.projects.apsdvsfusion.gui.NonGLImageDisplay;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeBuilder;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeNode;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.IllegalExpressionException;

/**
 * @author Dennis
 *
 */
public class ExpressionBasedSpatialInputKernel extends SignalTransformationKernel {
	int width = -1, height = -1;

	int centerX, centerY;
	int offsetX, offsetY;

//	String onExpressionString = "0.01";
	String expressionString = "0";
	
	Object convolutionValuesLock = new Object();
	
//	ExpressionTreeNode onExpressionTree = null;
//	ExpressionTreeNode offExpressionTree = null;
	
	boolean evaluateExpressionAsReceptiveField = true;
	float[][] convolutionValues = null;
	
	
	public synchronized float[][] getConvolutionValues() {
		return convolutionValues;
	}

//	public synchronized float[][] getOffConvolutionValues() {
//		return offConvolutionValues;
//	}
//
//	float[][] offConvolutionValues = null;

	/**
	 * 
	 */
	public ExpressionBasedSpatialInputKernel(int width, int height, Preferences prefs) {
		super("ExpressionBasedSpatialInputKernel",prefs);
		changeSize(width, height);
		addExcludedProperty("centerX");
		addExcludedProperty("centerY");
		addExcludedProperty("expressionString");
		
	}
	
//	@Deprecated
//	public ExpressionBasedSpatialInputKernel(int width, int height, Preferences parentPrefs,	String nodeName) {
//		super("ExpressionBasedSpatialInputKernel",parentPrefs, nodeName);
//		changeSize(width, height);
//	}
	
	public String getExpressionString() {
		return expressionString;
	}


	public void setExpressionString(String expressionString) {
		if (expressionString != null && !expressionString.equals("")) {
		try {
			synchronized (convolutionValuesLock) {
				this.convolutionValues = evaluateExpression(expressionString, convolutionValues, this.expressionString);
				convolutionValuesChanged();
			}
			getSupport().firePropertyChange("expressionString", this.expressionString, expressionString);
			this.expressionString = expressionString;
			final SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
			if (stf != null)
				stf.addExpressionString(expressionString);
		} catch (IllegalExpressionException e) {
		}
		}
	}

//	public String getOffExpressionString() {
//		return offExpressionString;
//	}
//
//	public void setOffExpressionString(String offExpressionString) {
//		try {
//			this.offConvolutionValues = evaluateExpression(offExpressionString, offConvolutionValues, this.offExpressionString);
//			this.offExpressionString = offExpressionString;
//		} catch (IllegalExpressionException e) {
//		}
//	}
	
	protected synchronized float[][] evaluateExpression(String expressionString, float[][] oldConvolutionValues, String oldString) throws IllegalExpressionException {
		ExpressionTreeNode et = ExpressionTreeBuilder.parseString(expressionString);
		float[][] newValues = new float[oldConvolutionValues.length][oldConvolutionValues[0].length];
		try {
			HashMap<String, Double> map = new HashMap<String, Double>();
			float centerX = (width-1) / 2f;
			float centerY = (height-1) / 2f;
			for (int x = 0; x < width; x++) {
				map.put("x", (double)(x - centerX));
				for (int y = 0; y < height; y++) {
					map.put("y", (double)(y - centerY));
					newValues[x][y] = (float)et.evaluate(map);
				}
			}
			
//			if (evaluateExpressionAsReceptiveField) {
//				// reflect if in receptive field mode:
//				for (int x = 0; x < width; x++) {
//					for (int y = 0; y < height; y++) {
//						oldConvolutionValues[width-x-1][height-y-1] = newValues[x][y];
//					}
//				}
//				return oldConvolutionValues;
//			}
//			else
				return newValues;
		}
		catch (RuntimeException e) {
			throw new IllegalExpressionException("Runtime Exception returned while evaluating expression tree!: "+e.toString());
		}
	}
	
	public synchronized void setWidth(int width) {
		getSupport().firePropertyChange("width", this.width, width);
		changeSize(width, height);
	}
	public synchronized void setHeight(int height) {
		getSupport().firePropertyChange("height", this.height, height);
		changeSize(width, height);
	}

	public synchronized int getWidth() {
		return width;
	}

	public synchronized int getHeight() {
		return height;
	}
	
	protected void convolutionValuesChanged() {
		updateConvolutionViewer();
	}
	
	public synchronized void changeSize(int width, int height) {
		if (width != this.width || height != this.height && width >= 0 && height >= 0) {
			synchronized (convolutionValuesLock) {
				this.width = width;
				this.height = height;
				try {
						convolutionValues = evaluateExpression(expressionString, new float[width][height], "0");
						convolutionValuesChanged();
	//				offConvolutionValues = evaluateExpression(offExpressionString, new float[width][height], "0");
				} catch (IllegalExpressionException e) {
				}
				
				this.centerX = (width-1)/2;
				this.centerY = (height-1)/2;
			}
			recomputeMappings();
		}
	}
	
	protected void recomputeMappings() {
		
	}
	
	public synchronized void setInputOutputSizes(int inputX, int inputY, int outputX, int outputY) {
		changeOffset((outputX - inputX) / 2 ,(outputX - outputY) / 2);
	}
	public synchronized void changeOffset(int offsetX, int offsetY) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}
	
	public synchronized int getCenterX() {
		return centerX;
	}

	public synchronized int getCenterY() {
		return centerY;
	}

	public synchronized void setCenterX(int centerX) {
		this.centerX = centerX;
		recomputeMappings();
	}
	
	public synchronized void setCenterY(int centerY) {
		this.centerY = centerY;
		recomputeMappings();
	}


	@Override
	public void signalAt(int tx, int ty, int time, double value) {
		if (isEnabled()) {
	//		
	//	}
	//	
	//	@Override
	////	public synchronized void apply(int tx, int ty, int time, Polarity polarity,
	////			FiringModelMap map, SpikeHandler spikeHandler) {
	//	public synchronized void apply(int tx, int ty, int time, Polarity polarity,
	//			FiringModelMap map, SpikeHandler spikeHandler) {
			tx = tx - centerX + offsetX;
			ty = ty - centerY + offsetY;
	//		int minx = Math.max(0, -tx), maxx = Math.min(width, map.getSizeX()-tx);
	//		int miny = Math.max(0, -ty), maxy = Math.min(height, map.getSizeY()-ty);
			int minx = Math.max(0, -tx), maxx = Math.min(width, getOutputWidth()-tx);
			int miny = Math.max(0, -ty), maxy = Math.min(height, getOutputHeight()-ty);
			tx += minx; ty += miny;
	//		float[][] convolutionValues;
	//		if (polarity == Polarity.On) 
	//			convolutionValues = onConvolutionValues;
	//		else
	//			convolutionValues = offConvolutionValues;
	//		tx -= map.getOffsetX();
	//		ty -= map.getOffsetY();
			final FiringModelMap map = getOutputMap();
			if (value == 1.0) {
				for (int x = minx; x < maxx; x++, tx++) 
					for (int y = miny, ity = ty; y < maxy; y++, ity++) 
						map.get(tx,ity).receiveSpike(convolutionValues[x][y], time);
			}
			else if (value == -1.0) {
				for (int x = minx; x < maxx; x++, tx++) 
					for (int y = miny, ity = ty; y < maxy; y++, ity++) 
						map.get(tx,ity).receiveSpike(-convolutionValues[x][y], time);
			}
			else if (value != 0.0) {
				for (int x = minx; x < maxx; x++, tx++) 
					for (int y = miny, ity = ty; y < maxy; y++, ity++) 
						map.get(tx,ity).receiveSpike(value * convolutionValues[x][y], time);
			}
		}
	}

////	@Override
//	public int getOffsetX() {
//		return offsetX;
//	}
//
////	@Override
//	public int getOffsetY() {
//		return offsetY;
//	}
//
////	@Override
//	public void setOffsetX(int offsetX) {
//		this.offsetX = offsetX;
//	}
//
////	@Override
//	public void setOffsetY(int offsetY) {
//		this.offsetY = offsetY;
//	}

	public synchronized void savePrefs(Preferences prefs, String prefString) {
		prefs.put(prefString+"expressionString", expressionString);
//		prefs.put(prefString+"offExpressionString", offExpressionString);
		prefs.putInt(prefString+"width", width);
		prefs.putInt(prefString+"height", height);
		prefs.putInt(prefString+"centerX", centerX);
		prefs.putInt(prefString+"centerY", centerY);
		prefs.putInt(prefString+"offsetX", offsetX);
		prefs.putInt(prefString+"offsetY", offsetY);
	}
	
	public synchronized void loadPrefs(Preferences prefs, String prefString) {
		int width = prefs.getInt(prefString+"width", this.width);
		int height = prefs.getInt(prefString+"height", this.height);
		changeSize(width, height);
		centerX = prefs.getInt(prefString+"centerX", centerX);
		centerY = prefs.getInt(prefString+"centerY", centerY);
		offsetX = prefs.getInt(prefString+"offsetX", offsetX);
		offsetY = prefs.getInt(prefString+"offsetY", offsetY);
		setExpressionString(prefs.get(prefString+"expressionString", expressionString));
//		setOnExpressionString(prefs.get(prefString+"onExpressionString", onExpressionString));
//		setOffExpressionString(offExpressionString = prefs.get(prefString+"offExpressionString", offExpressionString));
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}
	
	JComboBox expressionComboBox = new JComboBox();
	PropertyChangeListener usedExpressionsListener = null;
	NonGLImageDisplay convolutionViewer = null;
	
	@Override
	public JComponent createCustomControls() {
		JComponent parentComponent = super.createCustomControls();
		JPanel myPanel = new JPanel();
		myPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.gridy = 0;
		gbc.gridx = 0;
		gbc.fill = GridBagConstraints.BOTH;

		myPanel.add(parentComponent, gbc);
		gbc.gridy++;
		
		JPanel expressionPanel = new JPanel();
		expressionPanel.setLayout(new BoxLayout(expressionPanel,BoxLayout.X_AXIS));
		
		JLabel label = new JLabel("Expression:");
		label.setFont(label.getFont().deriveFont(10f));
		expressionPanel.add(label);
		
		expressionComboBox.setEditable(true);
		expressionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setExpressionString((String)expressionComboBox.getSelectedItem());
				if (!getExpressionString().equals(expressionComboBox.getSelectedItem()))
					expressionComboBox.setSelectedItem(getExpressionString());
			}
		});
		expressionComboBox.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent arg0) {
				setExpressionString((String)expressionComboBox.getSelectedItem());
				if (!getExpressionString().equals(expressionComboBox.getSelectedItem()))
					expressionComboBox.setSelectedItem(getExpressionString());
			}
			@Override
			public void focusGained(FocusEvent arg0) {		}
		});
		getSupport().addPropertyChangeListener("expressionString",new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (!evt.getNewValue().equals(expressionComboBox.getSelectedItem())) {
					expressionComboBox.setSelectedItem(evt.getNewValue());
				}
			}
		});
		expressionComboBox.setFont(myComboBox.getFont().deriveFont(10f));

		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		if (stf != null) {
			usedExpressionsListener = new PropertyChangeListener() {
				@SuppressWarnings("unchecked")
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					fillExpressionsComboBox((ArrayList<String>)evt.getNewValue());
				}
			};
			stf.getSupport().addPropertyChangeListener("usedExpressionStrings", usedExpressionsListener);
			fillExpressionsComboBox(stf.getUsedExpressionStrings());
		}
		expressionPanel.add(expressionComboBox);
		
		myPanel.add(expressionPanel, gbc);
		gbc.gridy++;
		
		convolutionViewer = new NonGLImageDisplay(getWidth(), getHeight());
		convolutionViewer.setPreferredSize(new Dimension(250,250));
		CollapsablePanel kernelViewerPanel = new CollapsablePanel("Kernel shape",convolutionViewer);
		updateConvolutionViewer();
		
		myPanel.add(kernelViewerPanel, gbc);
		gbc.gridy++;
		
		return myPanel;
	}
	
	protected void disconnectKernel() {
		SpatioTemporalFusion stf = SpatioTemporalFusion.getInstance(this);
		if (stf != null && usedExpressionsListener != null) {
			stf.getSupport().removePropertyChangeListener(usedExpressionsListener);
		}
		super.disconnectKernel();
	}
	
	
    public void updateConvolutionViewer() {
    	final float[][] convolutionValues = this.convolutionValues;
		if (convolutionViewer != null && convolutionValues != null) {
	        float max=Float.NEGATIVE_INFINITY;
	        float min=Float.POSITIVE_INFINITY;
	        for (int i=0; i<convolutionValues.length; i++)
	            for (int j=0; j<convolutionValues[i].length; j++)
	            {   max=Math.max(max,convolutionValues[i][j]);
	                min=Math.min(min,convolutionValues[i][j]);
	            }
	        
	        max=Math.max(max,min+Float.MIN_VALUE);
	        
	        max=Math.abs(max);
	        min=Math.abs(min);
	        final float absmax=Math.max(min,max);
	        
	        max=absmax;
	        min=-absmax;
	
	                
	//        disp.setPreferredSize(new Dimension(300,300));
	        if (!SwingUtilities.isEventDispatchThread()) {
		        SwingUtilities.invokeLater(new Runnable() {
					
					@Override
					public void run() {
				        if (convolutionViewer.getSizeX() != convolutionValues.length || convolutionViewer.getSizeY() != convolutionValues[0].length)
				        	convolutionViewer.setImageSize(convolutionValues.length,convolutionValues[0].length);
						for (int x = 0; x < convolutionValues.length; x++)
							for (int y = 0; y < convolutionValues[x].length; y++) {
								int mx = convolutionValues.length - x - 1;
								int my = convolutionValues[x].length - y - 1;
								float val = convolutionValues[x][y];
								if (val > absmax)
									convolutionViewer.setPixmapRGB(mx, my, 1.0f, 0, 0);
								else if (val > 0)
									convolutionViewer.setPixmapRGB(mx, my, val / absmax, 0, 0);
								else if (val < -absmax)
									convolutionViewer.setPixmapRGB(mx, my, 0, 0, 1.0f);
								else
									convolutionViewer.setPixmapRGB(mx, my, 0, 0, -val / absmax);
							}
				        convolutionViewer.repaint();
					}
				});
	        }
	        else {
		        if (convolutionViewer.getSizeX() != convolutionValues.length || convolutionViewer.getSizeY() != convolutionValues[0].length)
		        	convolutionViewer.setImageSize(convolutionValues.length,convolutionValues[0].length);
				for (int x = 0; x < convolutionValues.length; x++)
					for (int y = 0; y < convolutionValues[x].length; y++) {
						int mx = convolutionValues.length - x - 1;
						int my = convolutionValues[x].length - y - 1;
						float val = convolutionValues[x][y];
						if (val > absmax)
							convolutionViewer.setPixmapRGB(mx, my, 1.0f, 0, 0);
						else if (val > 0)
							convolutionViewer.setPixmapRGB(mx, my, val / absmax, 0, 0);
						else if (val < -absmax)
							convolutionViewer.setPixmapRGB(mx, my, 0, 0, 1.0f);
						else
							convolutionViewer.setPixmapRGB(mx, my, 0, 0, -val / absmax);
					}
		        convolutionViewer.repaint();
	        }
		}
    }
	
	
	public void fillExpressionsComboBox(ArrayList<String> items) {
		for (int i = 0; i < items.size(); i++) {
			if (expressionComboBox.getItemCount() <= i || !expressionComboBox.getItemAt(i).equals(items.get(i)))
				expressionComboBox.insertItemAt(items.get(i), i);
		}
//		expressionComboBox.setMaximumSize(new Dimension(200,(int)expressionComboBox.getSize().getHeight()));
		
//		Stack<Integer> toRemove = new Stack<Integer>();
//		for (int i = items.size(); i < expressionComboBox.getItemCount(); i++) {
//			if (items.contains(expressionComboBox.getItemAt(i)))
//					toRemove.push(i);
//		}
//		while (toRemove.size() > 0) {
//			expressionComboBox.removeItemAt(toRemove.pop());
//		}
	}

}
