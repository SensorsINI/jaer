package ch.unizh.ini.jaer.projects.multitracking;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;


public class EasyRecordWindows extends JFrame implements ActionListener {
	private easyRecordInterface inter;
	private JComboBox liste1;
	private JComboBox liste2;
	private JComboBox liste3;
    private Boolean isRecording=false;
    private JButton startStopButton;
	private JTextField textboxName;
	private JTextField textboxIndex;

	public EasyRecordWindows(easyRecordInterface interf){
		super();
		this.inter=interf;
		build();//On initialise notre fenetre
	}



	private void build(){

		isRecording = false;
		setTitle("windows"); //On donne un titre l'application
		setSize(400,200); //On donne une taille a notre fenetre
		setLocationRelativeTo(null); //On centre la fenetre sur l'ecran
		setResizable(false); //On interdit la redimensionnement de la fenetre
		//setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); //On dit � l'application de se fermer lors du clic sur la croix
		setContentPane(buildContentPanel());
	}


   public JPanel buildContentPanel(){
		JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout());

		Object[] elements = new Object[]{"Walking", "Running", "Jumping","Hopping","Kick","Punch","Waving","Circle","figure8","Clapping","Test","Calib"};
		Object[] direction = new Object[]{"up","down","_"};
		Object[] side = new Object[]{"left","right","_"};
		liste1 = new JComboBox(elements);
		liste2 = new JComboBox(direction);
		liste3 = new JComboBox(side);
		panel.add(liste1);
		panel.add(liste2);
		panel.add(liste3);


	    textboxName = new JTextField("name");
		panel.add(textboxName);
		textboxIndex = new JTextField("index");
		panel.add(textboxIndex);


		startStopButton = new JButton("Start");
		panel.add(startStopButton);
		startStopButton.addActionListener(this);

		return panel;
	}

	public JComboBox getListe1(){
		return liste1;
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		Object source = e.getSource();

		if((source==startStopButton) && (isRecording==false)){
			isRecording=true;
			inter.sartRecording(getTitle(), "2.0");
			startStopButton.setText("Stop");
		} else if((source==startStopButton) && (isRecording==true)){
			isRecording=false;
			inter.stopLogging();
			startStopButton.setText("Start");
		}
	}

	@Override
	public String getTitle(){
		return textboxName.getText()+textboxIndex.getText()+liste1.getSelectedItem().toString()+liste2.getSelectedItem().toString()+liste3.getSelectedItem().toString();

}


}
