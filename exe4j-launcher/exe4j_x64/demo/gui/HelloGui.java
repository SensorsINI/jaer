/*
 *   GUI application demonstration for exe4j
 */

import com.exe4j.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HelloGui extends JFrame {

    private HelloGui() {

        setSize(600, 400);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - 600) / 2, (screenSize.height - 400) / 2);
        setTitle("Hello World GUI");

        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem menuItem = new JMenuItem("Exit");
        menuItem.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        System.exit(0);
                    }
                }
        );

        menu.add(menuItem);
        menuBar.add(menu);
        setJMenuBar(menuBar);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JLabel helloLabel = new JLabel("Hello World!");
        helloLabel.setFont(helloLabel.getFont().deriveFont(50f));
        helloLabel.setHorizontalAlignment(SwingConstants.CENTER);
        getContentPane().add(helloLabel, BorderLayout.CENTER);


        Box box = Box.createVerticalBox();
        box.setBorder(createEmptyBorder());
        box.add(new JLabel(" * Start hello_gui.exe again to see how startup notification works"));
        box.add(new JLabel(" * Quit and pass \"fail\" as an argument to hello_gui.exe to see what happens for a startup failure"));
        getContentPane().add(box, BorderLayout.SOUTH);
    }

    private EmptyBorder createEmptyBorder() {
        return new EmptyBorder(5, 5, 5, 5);
    }

    private static void printToConsole(String message) {
        // In order to see the following output, please start the launcher from a console window
        // with the parameter -console
        //
        // GUI applications usually cannot access the console if they were started from a console window
        // Since the "Allow -console" parameter option was selected in the launcher configuration of the hello_gui
        // executable, the console is acquired by the launcher and stdout will be printed to it
        System.out.println(message);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        Controller.writeMessage("Initializing giant application ...");
        Thread.sleep(1000);
        Controller.writeMessage("Opening complex main window ...");
        Thread.sleep(1000);
        if (args.length == 1 && args[0].equals("fail")) {
            throw new RuntimeException("I was asked to fail");
        } else {
            final HelloGui hello = new HelloGui();
            printToConsole("Hello world");


            // startup notification on Microsoft Windows
            Controller.registerStartupListener(new Controller.StartupListener() {
                @Override
                public void startupPerformed(final String parameters) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            String message = "I've been started again, with parameters \"" + parameters + "\".";
                            JOptionPane.showMessageDialog(hello, message, "Hello World", JOptionPane.INFORMATION_MESSAGE);
                            printToConsole(message);
                        }
                    });
                }
            });

            hello.setVisible(true);
        }
    }

}