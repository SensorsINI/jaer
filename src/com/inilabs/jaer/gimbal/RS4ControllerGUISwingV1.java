/*
 * Copyright (C) 2024 rjd.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package com.inilabs.jaer.gimbal;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;


import com.inilabs.birdland.gimbal.RS4ControllerV2;
import static com.inilabs.jaer.gimbal.GimbalBase.rs4controllerGUI;

public class RS4ControllerGUISwingV1 extends JFrame {

    private RS4ControllerV2 controller;
    private JLabel currentStatusLabel;
    private JSlider yawSlider, rollSlider, pitchSlider, focusSlider, yawSpeedSlider, rollSpeedSlider, pitchSpeedSlider, deltaYawSlider, timeForActionSlider;
    private JTextField yawInput, rollInput, pitchInput, yawSpeedInput, rollSpeedInput, pitchSpeedInput;
    private float focus = 10;
    private int timeForAction = 0x14; // Default value for timeForAction
     private Timer statusUpdateTimer;

    public RS4ControllerGUISwingV1() {
        setTitle("RS4 Gimbal Controller");
        setSize(800, 600);
     //   setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
       setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
       
        setLocationRelativeTo(null);

        controller = RS4ControllerV2.getInstance();

        // Set up the UI
        setupUI();
        
            // Start the status update timer
        startStatusUpdateTimer();
        
     //   setVisible(true);
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Status label
        currentStatusLabel = new JLabel("Current Status - Yaw: 0.0, Roll: 0.0, Pitch: 0.0");
        currentStatusLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        currentStatusLabel.setForeground(Color.RED);

        mainPanel.add(currentStatusLabel, BorderLayout.NORTH);

        // Sliders and inputs panel
        JPanel controlPanel = new JPanel(new GridLayout(6, 3, 10, 10));

        // Create sliders and text fields
        yawSlider = createSlider(-180, 180, 0);
        rollSlider = createSlider(-180, 180, 0);
        pitchSlider = createSlider(-180, 180, 0);
        focusSlider = createSlider(0, 100, (int) focus);
        yawSpeedSlider = createSlider(-360, 360, 0);
        rollSpeedSlider = createSlider(-360, 360, 0);
        pitchSpeedSlider = createSlider(-360, 360, 0);
        deltaYawSlider = createSlider(-60, 60, 0);
        timeForActionSlider = createSlider(0, 255, timeForAction);
        timeForActionSlider.setOrientation(JSlider.VERTICAL);

        yawInput = new JTextField("0", 5);
        rollInput = new JTextField("0", 5);
        pitchInput = new JTextField("0", 5);
        yawSpeedInput = new JTextField("0", 5);
        rollSpeedInput = new JTextField("0", 5);
        pitchSpeedInput = new JTextField("0", 5);

        // Add action listeners for sliders to update controller
        addSliderListeners();

        // Populate control panel with labels, sliders, and text fields
        addControlComponents(controlPanel);

        mainPanel.add(controlPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addControlButtons(buttonPanel);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

   
    
    
    private JSlider createSlider(int min, int max, int value) {
    JSlider slider = new JSlider(JSlider.HORIZONTAL, min, max, value);
    slider.setMajorTickSpacing(90);
    slider.setMinorTickSpacing(10);
    slider.setPaintTicks(true);
    slider.setPaintLabels(true);
    slider.setPreferredSize(new Dimension(300, 50)); // Increase width and height for better legibility
    return slider;
}

    private void addSliderListeners() {
        yawSlider.addChangeListener(e -> controller.setPose((float) yawSlider.getValue(), controller.getRoll(), controller.getPitch()));
        rollSlider.addChangeListener(e -> controller.setPose(controller.getYaw(), (float) rollSlider.getValue(), controller.getPitch()));
        pitchSlider.addChangeListener(e -> controller.setPose(controller.getYaw(), controller.getRoll(), (float) pitchSlider.getValue()));
    }

    
    private void addControlComponents(JPanel panel) {
    panel.setLayout(new GridLayout(10, 3, 10, 10)); // Adjust grid layout for 10 rows and 3 columns

    panel.add(new JLabel("Yaw:"));
    panel.add(yawSlider);
    panel.add(yawInput);

    panel.add(new JLabel("Roll:"));
    panel.add(rollSlider);
    panel.add(rollInput);

    panel.add(new JLabel("Pitch:"));
    panel.add(pitchSlider);
    panel.add(pitchInput);

    panel.add(new JLabel("Focus:"));
    panel.add(focusSlider);
    panel.add(new JLabel("")); // Placeholder for alignment

    panel.add(new JLabel("Yaw Speed:"));
    panel.add(yawSpeedSlider);
    panel.add(yawSpeedInput);

    panel.add(new JLabel("Roll Speed:"));
    panel.add(rollSpeedSlider);
    panel.add(rollSpeedInput);

    panel.add(new JLabel("Pitch Speed:"));
    panel.add(pitchSpeedSlider);
    panel.add(pitchSpeedInput);

    panel.add(new JLabel("Delta Yaw:"));
    panel.add(deltaYawSlider);
    panel.add(new JLabel("")); // Placeholder for alignment

    panel.add(new JLabel("Time for Action:"));
    panel.add(timeForActionSlider);
    panel.add(new JLabel("")); // Placeholder for alignment
}


    private void addControlButtons(JPanel panel) {
        JButton resetButton = new JButton("Reset All");
        JButton sendPositionButton = new JButton("Send Position Control");
        JButton sendSpeedButton = new JButton("Send Speed Control");
        JButton sendDeltaYawButton = new JButton("Send Delta Yaw");
         JButton closeButton = new JButton("Close");
        JButton exitButton = new JButton("Exit");
        exitButton.setBackground(Color.RED);
        

        resetButton.addActionListener(e -> resetControls());
        sendPositionButton.addActionListener(e -> sendPositionControl());
        sendSpeedButton.addActionListener(e -> sendSpeedControl());
        sendDeltaYawButton.addActionListener(e -> sendDeltaYaw());
        closeButton.addActionListener(e -> setVisible(false));
        exitButton.addActionListener(e -> System.exit(0));

        panel.add(resetButton);
        panel.add(sendPositionButton);
        panel.add(sendSpeedButton);
        panel.add(sendDeltaYawButton);
        panel.add(closeButton);
        panel.add(exitButton);
    }

    private void resetControls() {
        yawSlider.setValue(0);
        rollSlider.setValue(0);
        pitchSlider.setValue(0);
        yawSpeedSlider.setValue(0);
        rollSpeedSlider.setValue(0);
        pitchSpeedSlider.setValue(0);
        focusSlider.setValue(10);
        timeForActionSlider.setValue(0x14);

        yawInput.setText("0");
        rollInput.setText("0");
        pitchInput.setText("0");
        yawSpeedInput.setText("0");
        rollSpeedInput.setText("0");
        pitchSpeedInput.setText("0");
    }

    private void sendPositionControl() {
        controller.setPose(
            Float.parseFloat(yawInput.getText()),
            Float.parseFloat(rollInput.getText()),
            Float.parseFloat(pitchInput.getText())
        );
    }

    private void sendSpeedControl() {
        controller.setSpeedControl(
            (float) yawSpeedSlider.getValue(),
            (float) rollSpeedSlider.getValue(),
            (float) pitchSpeedSlider.getValue()
        );
    }

    private void sendDeltaYaw() {
        controller.setDeltaPose((float) deltaYawSlider.getValue(), 0f, 0f);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RS4ControllerGUISwingV1::new);
    }
    
      private void startStatusUpdateTimer() {
        // Create a Timer that updates the currentStatusLabel every 500 ms
        statusUpdateTimer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateStatusLabel();
            }
        });
        statusUpdateTimer.start();
    }

    private void updateStatusLabel() {
        float yaw = controller.getYaw();
        float roll = controller.getRoll();
        float pitch = controller.getPitch();
        currentStatusLabel.setText(String.format("Current Status - Yaw: %.1f, Roll: %.1f, Pitch: %.1f", yaw, roll, pitch));
    }

    // Add a method to stop the timer when closing the application
    @Override
    public void dispose() {
        if (statusUpdateTimer != null && statusUpdateTimer.isRunning()) {
            statusUpdateTimer.stop();
        }
        super.dispose();
    }
    
    
}



