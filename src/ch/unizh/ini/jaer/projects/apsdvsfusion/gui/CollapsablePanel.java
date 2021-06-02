package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

  
public class CollapsablePanel extends JPanel {  
  
    /**
	 * 
	 */
	private static final long serialVersionUID = 7317872465701427906L;
	private boolean selected;  
    JPanel contentPanel;  
    /**
	 * @return the contentPanel
	 */
	HeaderPanel headerPanel;
	String title;
	
  
    private class HeaderPanel extends JPanel implements MouseListener {  
        /**
		 * 
		 */
		private static final long serialVersionUID = -8883518187114071940L;
//		String text;  
        Font font;  
        final int OFFSET = 2;//, PAD = 5;  
  
        public HeaderPanel(/*String text*/) {  
            addMouseListener(this);  
//            this.text = text;  
            font = new Font("sans-serif", Font.PLAIN, 12);  
            // setRequestFocusEnabled(true);  
            setPreferredSize(new Dimension(200, 20));  
//            int w = getWidth();  
//            int h = getHeight();  
  
            /*try { 
                open = ImageIO.read(new File("images/arrow_down_mini.png")); 
                closed = ImageIO.read(new File("images/arrow_right_mini.png")); 
            } catch (IOException e) { 
                e.printStackTrace(); 
            }*/  
  
        }  
  
        protected void paintComponent(Graphics g) {  
            super.paintComponent(g);  
            Graphics2D g2 = (Graphics2D) g;  
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  
                    RenderingHints.VALUE_ANTIALIAS_ON);  
            int h = getHeight();  
            int width = getWidth();
        	Polygon arrow = new Polygon();
//        	Polygon leftArrow = new Polygon();

//                g2.drawImage(closed, PAD, 0, h, h, this); 
            g2.setFont(font);  
            FontRenderContext frc = g2.getFontRenderContext();  
            LineMetrics lm = font.getLineMetrics(title, frc);  
            float height = lm.getAscent() + lm.getDescent();  
            float x = OFFSET;  
            float y = (h + height) / 2 - lm.getDescent();  
            g2.drawString(title, x, y);
            int th = ((int)height)-2;
            if (selected) {
            	arrow.addPoint(width - 3*th/2 + 1, h/2 - th/4);
            	arrow.addPoint(width - th/2 - 1, h/2 - th/4);
            	arrow.addPoint(width - th, h/2 + th/4 );
            	g2.fillPolygon(arrow);
            }
//                g2.drawImage(open, PAD, 0, h, h, this); 
            else { 
            	arrow.addPoint(width - 3*th/4, h/2 - (th-2)/2);
            	arrow.addPoint(width - 3*th/4, h/2 + (th-2)/2);
            	arrow.addPoint(width - 5*th/4, h /2);
            	g2.fillPolygon(arrow);
            }
        }  
  
        public void mouseClicked(MouseEvent e) {  
            toggleSelection();  
        }  
  
        public void mouseEntered(MouseEvent e) {  
        }  
  
        public void mouseExited(MouseEvent e) {  
        }  
  
        public void mousePressed(MouseEvent e) {  
        }  
  
        public void mouseReleased(MouseEvent e) {  
        }  
  
    }  
  

    
    public CollapsablePanel(String title, JPanel contentPanel) {  
        super(new GridBagLayout());  
        this.title = title;
        
        GridBagConstraints gbc = new GridBagConstraints();  
        gbc.insets = new Insets(1, 3, 0, 3);  
        gbc.weightx = 1.0;  
        gbc.fill = GridBagConstraints.HORIZONTAL;  
        gbc.gridwidth = GridBagConstraints.REMAINDER;  
        
        selected = false;  
        headerPanel = new HeaderPanel();  
  
        setBackground(new Color(200, 200, 220));  
        this.contentPanel = contentPanel;  
  
        add(headerPanel, gbc);  

        
        add(contentPanel, gbc);  
        contentPanel.setVisible(false);  
  
        JLabel padding = new JLabel();  
        gbc.weighty = 1.0;  
        add(padding, gbc);  
  
    }  
    
    
    
    /**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}



	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
		headerPanel.repaint();
	}



	public CollapsablePanel(String title) {
    	this(title, new JPanel());
    }
  
	protected JPanel getContentPanel() {
		return contentPanel;
	}

    public void setExpanded(boolean expanded) {
    	if (expanded != selected)
    		toggleSelection();
    }
	
    public boolean isExpanded() {
    	 return this.selected;
    }
    
    public void toggleSelection() {  
        selected = !selected;  
  
        if (contentPanel.isShowing())  
            contentPanel.setVisible(false);  
        else  
            contentPanel.setVisible(true);  
  
        validate();  
        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
        if (frame != null)
        	frame.pack();
  
        headerPanel.repaint();  
    }  
    public static void main(String[] args) {  
    	  
        CollapsablePanel cp = new CollapsablePanel("test", buildPanel());  
  
        JFrame f = new JFrame();  
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  
        f.getContentPane().add(new JScrollPane(cp));  
        f.setSize(360, 500);  
        f.setLocation(200, 100);  
        f.setVisible(true);  
    }  
  
    public static JPanel buildPanel() {  
        GridBagConstraints gbc = new GridBagConstraints();  
        gbc.insets = new Insets(2, 1, 2, 1);  
        gbc.weightx = 1.0;  
        gbc.weighty = 1.0;  
  
        JPanel second = new JPanel();
        second.add(new JButton("Hello"));
        JPanel p1 = new JPanel(new GridBagLayout());  
        gbc.gridwidth = GridBagConstraints.RELATIVE;  
        p1.add(new JButton("button 1"), gbc);  
        gbc.gridwidth = GridBagConstraints.REMAINDER;  
        p1.add(new JButton("button 2"), gbc);  
        gbc.gridwidth = GridBagConstraints.RELATIVE;  
        p1.add(new JButton("button 3"), gbc);  
        gbc.gridwidth = GridBagConstraints.REMAINDER;  
        p1.add(new JButton("button 4"), gbc);  
        
        CollapsablePanel secondcp = new CollapsablePanel("second", second);
        
        CollapsablePanel thirdcp = new CollapsablePanel("third", p1);
  
        JPanel p2 = new JPanel();
        p2.setLayout(new GridBagLayout());
        GridBagConstraints gbc2 = new GridBagConstraints();  
        gbc2.insets = new Insets(2, 1, 2, 1);  
        gbc2.weightx = 1.0;  
        gbc2.weighty = 1.0;  
        gbc2.gridy = 0;
        p2.add(secondcp,gbc2);
        gbc2.gridy = 1;
        p2.add(thirdcp,gbc2);
        p2.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK));
        return p2;  
    }  
  
}  