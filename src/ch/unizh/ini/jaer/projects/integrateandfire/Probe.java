/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * Probe.java
 *
 * Created on Sep 16, 2011, 11:01:25 AM
 */

package ch.unizh.ini.jaer.projects.integrateandfire;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 *
 * @author tobi
 */
public class Probe extends Plotter  implements ActionListener,WindowListener{
    
    
    // My stuff
    
    
    Probe h;

    int currentIX=0;

    Network.Unit Unit;


    float minMP;
    float maxMP;

    float minFR;
    float maxFR;

    float tc;       // Time constant when measuring FF.

    boolean deleted=false;

    //unitchange L;

    //UnitProbe self;

    /*class unitchange implements ActionListener
    {   
    }*/

    public void UnitProbe(LIFNet NN_){load(NN_);}
    
    @Override public void actionPerformed(ActionEvent e) {

            if (e.getSource()==h.editUnit)
            {    editUnit();
            }
            else if (e.getSource()==h.comboFanout)
            {   comboFanout();
            }

        }

    @Override
    public void init()
    {
        //L=new unitchange();
        //s//elf=this;

        h=this; // For old times's sake.

        h.setVisible(true);

        h.labTotal.setText("("+NN.nUnits()+" in total)");

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

//        if (x!=-1)
//        {   int n=NN.c[currentIX][x];
//            //newunit(n);
//        }
    }

    @Override
    public void refresh()
    {   newunit(currentIX);
    }
    
    private void newunit(int n)
    {
        
        if (n>=NN.nUnits())
        {    n=NN.nUnits()-1;}
        else if (n<0)
        {      n=0;    }

        currentIX=n;
        Unit=NN.getUnit(n);
        
        h.editUnit.setText(""+n);

        //minMP=-Unit.thresh;
        //maxMP=Unit.thresh*(float)1.2;

        minMP=0;
        maxMP=0;
        minFR=0;
        maxFR=0;
        
        this.jTextInfo.setText(Unit.getInfo());
//        this.labTag.setText(""+Unit.getName());
//        h.labThresh.setText(""+NN.N[currentIX].thresh);
//        h.labTC.setText(""+NN.N[currentIX].tau);

        updateMPextreme();

        // Build fanout list
        h.comboFanout.removeAllItems();
        for (int i=0;i<NN.getWeights(n).length;i++)
        {    h.comboFanout.addItem(NN.getConnections(n)[i]+" w:"+NN.getWeights(n)[i]);
        }
        
        // Build network list
        h.comboNet.removeAllItems();
        for (int i=0;i<this.NA.numNets;i++)
        {    h.comboNet.addItem(""+i);
        }
        
        
        if (Unit.getName().length()==0)
            h.setTitle("Net "+currentNet+", Unit "+n);
        else
            h.setTitle("Net "+currentNet+", Unit "+n+" '"+Unit.getName()+"'");
        
        //h.comboFanout.enable();
        update(lasttimestamp);
    }

    void updateMPextreme()
    {
        h.labMPmin.setText(""+minMP);
        h.labMPmax.setText(""+maxMP);
    }

    void updateFRextreme()
    {   h.labFRmin.setText(""+minFR);
        h.labFRmax.setText(""+maxFR);
    }

    @Override public void update(int timestamp)
    {
        if (Unit==null) 
            return;
        
        float vm=Unit.getVsig(timestamp);
        
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

        
        // Calling me laaaaaazyyy
        float fr=Unit.getAsig();
        if (fr<minFR)
        {   minFR=fr;
            updateFRextreme();
        }
        else if (fr>maxFR)
        {   maxFR=fr;
            updateFRextreme();
        }
        h.labFF.setText(""+fr);
        h.barFF.setValue((int) (100*(fr-minFR)/(maxFR-minFR)) );
        
        
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

    
    
    //==========================================================================
    //                THEIR STUFF
    
    
    /** Creates new form Probe */
    public Probe() {
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        menuBar1 = new java.awt.MenuBar();
        menu1 = new java.awt.Menu();
        menu2 = new java.awt.Menu();
        menuBar2 = new java.awt.MenuBar();
        menu3 = new java.awt.Menu();
        menu4 = new java.awt.Menu();
        jLabel1 = new javax.swing.JLabel();
        editUnit = new javax.swing.JTextField();
        labTotal = new javax.swing.JLabel();
        barMP = new javax.swing.JProgressBar();
        jLabel3 = new javax.swing.JLabel();
        labMP = new javax.swing.JLabel();
        labFF = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        barFF = new javax.swing.JProgressBar();
        labMPmin = new javax.swing.JLabel();
        labMPmax = new javax.swing.JLabel();
        labFRmin = new javax.swing.JLabel();
        labFRmax = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        comboFanout = new javax.swing.JComboBox();
        jLabel5 = new javax.swing.JLabel();
        comboNet = new javax.swing.JComboBox();
        butRefresh = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextInfo = new javax.swing.JTextPane();

        menu1.setLabel("File");
        menuBar1.add(menu1);

        menu2.setLabel("Edit");
        menuBar1.add(menu2);

        menu3.setLabel("File");
        menuBar2.add(menu3);

        menu4.setLabel("Edit");
        menuBar2.add(menu4);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Unit:");

        editUnit.setText("1");
        editUnit.setName("editUnit"); // NOI18N

        labTotal.setText("total");

        jLabel3.setText("Membrane Potential");

        labMP.setText("vmem");

        labFF.setText("FF");

        jLabel6.setText("Firing Rate");

        labMPmin.setText("min");

        labMPmax.setText("max");

        labFRmin.setText("min");

        labFRmax.setText("max");

        jLabel11.setText("Fan Out:");

        comboFanout.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel5.setText("Network:");

        comboNet.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        comboNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboNetActionPerformed(evt);
            }
        });

        butRefresh.setText("refresh");
        butRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                butRefreshActionPerformed(evt);
            }
        });

        jScrollPane1.setViewportView(jTextInfo);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(editUnit, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(labTotal, javax.swing.GroupLayout.DEFAULT_SIZE, 58, Short.MAX_VALUE)
                        .addGap(46, 46, 46))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(labFRmin)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(labFRmax))
                            .addComponent(barFF, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(labFF))
                    .addComponent(jLabel6)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(comboFanout, 0, 150, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(comboNet, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(butRefresh))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(labMPmin)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(labMPmax))
                            .addComponent(jLabel3)
                            .addComponent(barMP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(labMP)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(50, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel5)
                    .addComponent(comboNet, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(editUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(labTotal))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel11)
                    .addComponent(comboFanout, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(butRefresh)
                        .addGap(20, 20, 20))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(barMP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(labMP, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(labMPmin)
                    .addComponent(labMPmax))
                .addGap(13, 13, 13)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(labFF)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(barFF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(labFRmin)
                    .addComponent(labFRmax))
                .addGap(30, 30, 30))
        );

        editUnit.getAccessibleContext().setAccessibleName("editUnit");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void butRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_butRefreshActionPerformed
        // TODO add your handling code here:
        refresh();
    }//GEN-LAST:event_butRefreshActionPerformed

    private void comboNetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboNetActionPerformed
        // TODO add your handling code here:
        this.setCurrentNet(this.comboNet.getSelectedIndex());
    }//GEN-LAST:event_comboNetActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Probe().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JProgressBar barFF;
    public javax.swing.JProgressBar barMP;
    private javax.swing.JButton butRefresh;
    public javax.swing.JComboBox comboFanout;
    public javax.swing.JComboBox comboNet;
    public javax.swing.JTextField editUnit;
    public javax.swing.JLabel jLabel1;
    public javax.swing.JLabel jLabel11;
    public javax.swing.JLabel jLabel3;
    public javax.swing.JLabel jLabel5;
    public javax.swing.JLabel jLabel6;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextPane jTextInfo;
    public javax.swing.JLabel labFF;
    public javax.swing.JLabel labFRmax;
    public javax.swing.JLabel labFRmin;
    public javax.swing.JLabel labMP;
    public javax.swing.JLabel labMPmax;
    public javax.swing.JLabel labMPmin;
    public javax.swing.JLabel labTotal;
    private java.awt.Menu menu1;
    private java.awt.Menu menu2;
    private java.awt.Menu menu3;
    private java.awt.Menu menu4;
    private java.awt.MenuBar menuBar1;
    private java.awt.MenuBar menuBar2;
    // End of variables declaration//GEN-END:variables

}
