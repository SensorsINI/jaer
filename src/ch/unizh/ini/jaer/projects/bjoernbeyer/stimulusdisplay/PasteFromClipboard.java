package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.*;

/**
 * ExcelAdapter enables Copy-Paste Clipboard functionality on JTables.
 * The clipboard data format used by the adapter is compatible with
 * the clipboard format used by Excel. This provides for clipboard
 * interoperability between enabled JTables and Excel. */
public class PasteFromClipboard implements ActionListener {
    private String rowstring,value;
    private final Clipboard system;
    private JTable tableToPaste ;
    
    /**
     * The Excel Adapter is constructed with a
     * JTable on which it enables Copy-Paste and acts
     * as a Clipboard listener.
     * @param myJTable  */
    public PasteFromClipboard(JTable myJTable){
        tableToPaste = myJTable;

        KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V,ActionEvent.CTRL_MASK,false);
        // Identifying the Paste KeyStroke 
        tableToPaste.registerKeyboardAction(this,"Paste",paste,JComponent.WHEN_FOCUSED);
        system = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    
    /**
     * Public Accessor methods for the Table on which this adapter acts.
     * @return the table that is used for pasting */
    public JTable getJTable() {
        return tableToPaste;
    }
    
    public void setJTable(JTable jTable1) {
        this.tableToPaste = jTable1;
    }
    
    /**
     * This method is activated on the Keystrokes we are listening to
     * in this implementation. Here it listens for Paste ActionCommands.
     * Paste is done by aligning the upper left corner of the selection with the
     * 1st element in the current selection of the JTable.
     * @param e */
    @Override public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().compareTo("Paste")==0) {
            int startRow=(tableToPaste.getSelectedRows())[0];
            int startCol=(tableToPaste.getSelectedColumns())[0];
            try {
                String trstring= (String)(system.getContents(this).getTransferData(DataFlavor.stringFlavor));

                StringTokenizer st1=new StringTokenizer(trstring,"\n");
                for(int i=0;st1.hasMoreTokens();i++) {
                rowstring=st1.nextToken();

                StringTokenizer st2=new StringTokenizer(rowstring,"\t");
                    for(int j=0;st2.hasMoreTokens();j++) {
                        value=(String)st2.nextToken();
                        if (startRow+i< tableToPaste.getRowCount()  && startCol+j< tableToPaste.getColumnCount()){
                            tableToPaste.setValueAt(Float.parseFloat(value),startRow+i,startCol+j);
                        }   
                    }
                }
            }
            catch(UnsupportedFlavorException | IOException | NumberFormatException ex){
                System.out.println(ex.getMessage());
            }
        }
    }
}