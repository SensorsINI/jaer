/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.neuralNetToolbox;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.awt.event.WindowStateListener;

/**
 *
 * @author tobi
 */


// Note: need to cancel updates once windows get closed!

public class NumberPlot extends Plotter implements ActionListener {
    
    NumberReader h;       // Numberreader object

    Controller c;         // Controller Object

    //UnitProbe[] P=new UnitProbe[0];

    List<UnitProbe> P = new ArrayList<UnitProbe>(0);

    void NumberPlot(SuperNetFilter F_, Network NN_){load(F_,NN_);}
    
    @Override public void init()
    {
        //NN=N;
        h=new NumberReader();
        h.setVisible(true);
        h.jSlider.setValue(10);

        // Prevent top layer from firing
        int i;
        for (i=1; i<11; i++){
            NN.N[NN.N.length-i].thresh=100000;
        }

        h.pushProbe.addActionListener(this);
        h.pushControl.addActionListener(this);

    }

    @Override public void update()
    {
        // Prevent top layer from firing
        float[] vout=new float[10];
        float vmax=-100000;
        int i,imax=0;
        for (i=0; i<10; i++){
            vout[i]=NN.N[NN.N.length-10+i].get_vmem(F.getLastTimestamp());
            if (vout[i]>vmax) {vmax=vout[i]; imax=i;}
        }

        if (vmax>0){
            h.jResult.setText("" + ((imax)%10));
        }
        else {
            h.jResult.setText("?");
        }

        float scalefac=(float) (h.jSlider.getValue()) /100;
        h.jBar1.setValue((int) (100/(1+Math.exp(-vout[1]*scalefac))));
        h.jBar2.setValue((int) (100/(1+Math.exp(-vout[2]*scalefac))));
        h.jBar3.setValue((int) (100/(1+Math.exp(-vout[3]*scalefac))));
        h.jBar4.setValue((int) (100/(1+Math.exp(-vout[4]*scalefac))));
        h.jBar5.setValue((int) (100/(1+Math.exp(-vout[5]*scalefac))));
        h.jBar6.setValue((int) (100/(1+Math.exp(-vout[6]*scalefac))));
        h.jBar7.setValue((int) (100/(1+Math.exp(-vout[7]*scalefac))));
        h.jBar8.setValue((int) (100/(1+Math.exp(-vout[8]*scalefac))));
        h.jBar9.setValue((int) (100/(1+Math.exp(-vout[9]*scalefac))));
        h.jBar0.setValue((int) (100/(1+Math.exp(-vout[0]*scalefac))));
        //NumDisp

        Iterator<UnitProbe> Pit = P.iterator();
	while (Pit.hasNext()) {
            Pit.next().update();
	}

    }

    @Override public void actionPerformed(ActionEvent e) {
        if (e.getSource()==h.pushProbe)
        {   UnitProbe p=new UnitProbe();
            p.load(F,NN);
            p.init();
            P.add(p);
        }
        else if (e.getSource()==h.pushControl)
        {    c=new Controller();
             c.load(F,NN);
             c.init();
        }
    }

    
}
