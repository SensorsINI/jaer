/*
 * Copyright (C) 2022 tobi.
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
package org.ine.telluride.jaer.tell2022;

// results of fishing attempt

import java.io.Serializable;


class FishingResult implements Serializable {

    boolean succeeded;
    float thetaOffsetDeg;
    long delayMs;

    public FishingResult(boolean succeeded, float thetaOffsetDeg, long delayMs) {
        this.succeeded = succeeded;
        this.thetaOffsetDeg = thetaOffsetDeg;
        this.delayMs = delayMs;
    }
    
}
