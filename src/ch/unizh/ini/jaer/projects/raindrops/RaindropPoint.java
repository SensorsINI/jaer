/*
 * Copyright (C) 2019 Tobi.
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
package ch.unizh.ini.jaer.projects.raindrops;

import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;

/**
 * Record for one droplet
 * @author Tobi
 */
public class RaindropPoint extends ClusterPathPoint {
    
    

    /**
     * Factory method that subclasses can override to create custom path points,
     * e.g. for storing different statistics
     *
     * @param x
     * @param y
     * @param t
     * @return a new ClusterPathPoint with x,y,t set. Other fields must be set
     * using methods.
     */
    public static ClusterPathPoint createPoint(float x, float y, int t) {
        return new RaindropPoint(x, y, t);
    }

    protected RaindropPoint(float x, float y, int t) {
        super(x, y, t);
    }

} // RaindropPoint
