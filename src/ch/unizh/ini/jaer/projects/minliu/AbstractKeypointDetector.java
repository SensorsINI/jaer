/*
 * Copyright (C) 2018 tobi.
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
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.npp.DvsFramer;
import net.sf.jaer.chip.AEChip;

/**
 * Base class for keypoint detectors
 * 
 * @author tobi
 */
abstract public class AbstractKeypointDetector extends DvsFramer {
    
    public AbstractKeypointDetector(AEChip chip) {
        super(chip);
    }
    
}
