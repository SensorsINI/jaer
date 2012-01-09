/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.neuralNetToolbox;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 *
 * @author tobi
 */
public class UnitProbe extends Plotter implements ActionListener,WindowListener{

    Probe h;

    int currentIX=0;

    Neuron Unit;


    float minMP;
    float maxMP;

    float minFF;
    float maxFF;

    float tc;       // Time constant when measuring FF.

    boolean deleted=false;

    //unitchange L;

    //UnitProbe self;

    /*class unitchange implements ActionListener
    {   
    }*/

    public void UnitProbe(SuperNetFilter F_, Network NN_){load(F_,NN_);}
    
    @Override public void actionPerformed(ActionEvent e) {

            if (e.getSource()==h.editUnit)
            {    editUnit();
            }
            else if (e.getSource()==h.comboFanout)
            {   comboFanout();
            }

        }

    public void init()
    {
        //L=new unitchange();
        //s//elf=this;

        h=new Probe();

        h.setVisible(true);

        h.labTotal.setText("("+NN.N.length+" in total)");

        h.editUnit.addActionListener(this);
        h.comboFanout.addActionListener(this);
        h.addWindowListener(this);

        newunit(0);

    }

    private void editUnit()
    {   int n=Integer.parseInt(h.editUnit.getText());
        newunit(n);
    }

    private void comboFanout()
    {   int x=h.comboFanout.getSelectedIndex();

        if (x!=-1)
        {   int n=NN.c[currentIX][x];
            //newunit(n);
        }
    }

    private void newunit(int n)
    {
        
        if (n>=NN.N.length)
        {    n=NN.N.length-1;}
        else if (n<0)
        {      n=0;    }

        currentIX=n;
        Unit=NN.N[n];
        
        h.editUnit.setText(""+n);

        //minMP=-Unit.thresh;
        //maxMP=Unit.thresh*(float)1.2;

        minMP=0;
        maxMP=0;

        h.labThresh.setText(""+NN.N[currentIX].thresh);
        h.labTC.setText(""+NN.N[currentIX].tau);

        updateMPextreme();

        //h.comboFanout.disable();
        h.comboFanout.removeAllItems();
        for (int i=0;i<NN.w[currentIX].length;i++)
        {    h.comboFanout.addItem(NN.c[currentIX][i]+" w:"+NN.w[currentIX][i]);
        }

        if (NN.N[currentIX].name.length()==0)
            h.setTitle("Unit "+n);
        else
            h.setTitle("Unit "+n+" '"+NN.N[currentIX].name+"'");
        
        //h.comboFanout.enable();
        update();
    }

    void updateMPextreme()
    {
        h.labMPmin.setText(""+minMP);
        h.labMPmax.setText(""+maxMP);
    }



    @Override public void update()
    {
        float vm=Unit.get_vmem(F.getLastTimestamp());

        if (vm<minMP)
        {   minMP=vm;
            updateMPextreme();
        }
        else if (vm>maxMP)
        {   maxMP=vm;
            updateMPextreme();
        }
            

        h.labMP.setText(""+vm);
        h.barMP.setValue((int) (100*(vm-minMP)/(maxMP-minMP)) );

    }



    @Override
    public void windowOpened(WindowEvent e) {
    }

    @Override
    public void windowClosing(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
        
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }


}

