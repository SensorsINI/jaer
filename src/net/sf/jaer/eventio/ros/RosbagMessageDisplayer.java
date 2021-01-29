/*
 * Copyright (C) 2018 Tobi Delbruck.
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
package net.sf.jaer.eventio.ros;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.ros.RosbagFileInputStream.MessageWithIndex;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;

/**
 * Parses and displays information from a ROS file that has messages that might
 * be of interest, e.g. the Traxxas Slash PWM signals used to control car
 * recorded in the ROS file as topic /dev/pwm. A concrete subclass should set
 * the topics and fields and parse them in the parseMessages method
 *
 * @author Tobi Delbruck
 */
@Description("Abstract class for subscribing to rosbag messages")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
abstract public class RosbagMessageDisplayer extends EventFilter2D {

    protected List<String> topics = new ArrayList(); // stores topics and their fields for each topic

    private RosbagFileInputStream rosbagInputStream;
    private boolean addedPropertyChangeListener = false;

    public RosbagMessageDisplayer(AEChip chip) {
        super(chip);
    }

    synchronized protected void addTopics(List<String> topics) {
        this.topics.addAll(topics);
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!addedPropertyChangeListener) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().getAePlayer().addPropertyChangeListener(this);
                addedPropertyChangeListener = true;
            }
        }
        return in;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            doAddSubscribers();
            return;
        }
        if (evt.getSource() == rosbagInputStream) {
            if (!isFilterEnabled()) {
                return;
            }

            if (evt.getNewValue() instanceof RosbagFileInputStream.MessageWithIndex) {
                MessageWithIndex msg = (RosbagFileInputStream.MessageWithIndex) (evt.getNewValue());
                MessageType msgType = msg.messageType;
                log.info("parsing message on topic " + msg.messageIndex.topic + " with timestamp " + msg.messageIndex.timestamp);
                parseMessage(msg);
            }
        }
    }

    /**
     * Parses the MessageWithIndex from the ROS file
     *
     * @param msg the message
     */
    abstract protected void parseMessage(MessageWithIndex msg);

    /**
     * Makes the GUI button to add subscriptions, run this long process in
     * worker thread that can be canceled.
     */
    synchronized public void doAddSubscribers() {
        if (!(chip.getAeInputStream() instanceof RosbagFileInputStream)) {
            log.warning("don't have RosbagFileInputStream yet, can't add topic subscriptions to it");
            return;
        }
        final ProgressMonitor progressMonitor = new ProgressMonitor(getChip().getFilterFrame(), "Add subscriptions", "", 0, 100);
        final SwingWorker<Void, Void> worker = new SwingWorker() {
            @Override
            protected Object doInBackground() throws Exception {
                try {
                    rosbagInputStream = (RosbagFileInputStream) (chip.getAeInputStream());
                    rosbagInputStream.addSubscribers(topics, RosbagMessageDisplayer.this, progressMonitor);
                } catch (InterruptedException ex) {
                    log.warning("canceled indexing ");
                } catch (BagReaderException ex) {
                    log.warning("exception in Bagfile: " + ex);
                }
                return null;
            }
        };
        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getSource() == worker) {
                    if (evt.getPropertyName().equals("progress")) {
                        progressMonitor.setProgress((Integer) evt.getNewValue());
                    }
                    if (progressMonitor.isCanceled()) {
                        worker.cancel(true);
                    }
                }
            }
        });

        worker.execute();
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

}
