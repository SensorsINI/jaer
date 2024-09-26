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
package com.inilabs.jaer.projects.inix.hardware.DJIRS4;

import java.util.List;

/**
 *
 * @author rjd chatgtp
 */

public class CRC32 {

    private static final int POLYNOMIAL = 0x04C11DB7;
    private static final int INITIAL_VALUE = 0xFFFFFFFF;  // Initial value for CRC32 calculation
    private static final int FINAL_XOR_VALUE = 0xFFFFFFFF;  // Final XOR value for CRC32

    private static int[] crcTable = new int[256];

    static {
        // Populate the CRC table for fast computation
        for (int i = 0; i < 256; i++) {
            int crc = i << 24;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x80000000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
            }
            crcTable[i] = crc & 0xFFFFFFFF;
        }
    }

    public static int init() {
        return INITIAL_VALUE;
    }

    public static int update(int crc, List<Byte> data) {
        for (Byte b : data) {
            crc = (crc << 8) ^ crcTable[((crc >>> 24) ^ (b & 0xFF)) & 0xFF];
        }
        return crc & 0xFFFFFFFF;
    }

    public static int finalize(int crc) {
        return crc ^ FINAL_XOR_VALUE;
    }
}
