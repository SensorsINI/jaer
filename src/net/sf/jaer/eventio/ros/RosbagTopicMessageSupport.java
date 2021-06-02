/*
 * Copyright (C) 2018 Tobi.
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

import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;
import javax.swing.ProgressMonitor;

/**
 * Allows subscribers to get PropertyChange events when ROS Message are read
 * from input stream
 *
 * @author Tobi
 */
public interface RosbagTopicMessageSupport {

    public void addSubscribers(List<String> topics, PropertyChangeListener listener, ProgressMonitor progressMonitor) throws InterruptedException, BagReaderException;

    public void removeTopic(String topic, PropertyChangeListener listener);
    
    public Collection<String> getMessageListenerTopics();

}
