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
package com.inilabs.jaer.projects.agent;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/** Key Points of the Interface:
*
*    start(): Starts the real-time agent.
*    stop(): Stops the real-time agent.
*    addEventListener(): Adds an AgentEventListener to the agent.
*    removeEventListener(): Removes an AgentEventListener from the agent.
*    isActive(): Returns whether the agent is active or not (useful to check if the agent is still running).
*
*    How It Works:
*
*    The RealTimeAgent class now implements the RealTimeAgentInterface which makes it clear what operations the agent must support.
*    This approach allows you to implement different types of agents with the same basic functionality but possibly different internal behaviors.
*    You can easily extend this system by creating additional real-time agents that implement RealTimeAgentInterface and have different run() behaviors or event handling logic.
*
 *  By using an interface, you can later create different types of real-time agents that might process different kinds of data or handle events in different ways, while still adhering to a consistent structure.
 *
 * @since October 2024
 * @author rjd chatgtp
 */

public class RealTimeAgent implements RealTimeAgentInterface, Runnable {

    private volatile boolean active; // Agent status
    private final ExecutorService executor; // Thread pool for real-time tasks
    private final List<AgentEventListener> eventListeners; // List of event listeners

    public RealTimeAgent() {
        this.active = true;
        this.executor = Executors.newSingleThreadExecutor(); // Single thread for the agent
        this.eventListeners = new ArrayList<>(); // List to store event listeners
    }

    @Override
    public void run() {
        while (active) {
            try {
                // Simulate real-time data processing (e.g., from sensors)
                String realTimeData = processRealTimeData();

                // Trigger an event when new data is processed
                triggerEvent("dataProcessed", realTimeData);
                
                // Sleep for a while to simulate periodic real-time checks
                Thread.sleep(1000); // 1 second delay between checks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Agent interrupted");
                triggerEvent("error", e);
            }
        }
    }

    private String processRealTimeData() {
        String data = "Data processed at " + System.currentTimeMillis();
        System.out.println(data);
        return data;
    }

    @Override
    public void stop() {
        this.active = false;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        triggerEvent("agentStopped", null);
    }

    @Override
    public void start() {
        executor.submit(this); // Starts the real-time agent in a new thread
        triggerEvent("agentStarted", null);
    }

   
    public void addEventListener(AgentEventListener listener) {
        eventListeners.add(listener);
    }
 
    public void removeEventListener(AgentEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private void triggerEvent(String eventType, Object eventData) {
        for (AgentEventListener listener : eventListeners) {
            listener.onEvent(eventType, eventData);
        }
    }

    public static void main(String[] args) {
        RealTimeAgent agent = new RealTimeAgent();
        
        // Adding a sample event listener
        agent.addEventListener(new AgentEventListener() {
            @Override
            public void onEvent(String eventType, Object eventData) {
                System.out.println("Event triggered: " + eventType);
                if (eventData != null) {
                    System.out.println("Event data: " + eventData);
                }
            }
        });
        
        agent.start(); // Start the real-time agent

        try {
            Thread.sleep(5000); // Let it run for 5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        agent.stop(); // Stop the agent
        System.out.println("Agent stopped.");
    }
}

