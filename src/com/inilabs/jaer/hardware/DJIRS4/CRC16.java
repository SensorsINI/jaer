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
package com.inilabs.jaer.hardware.DJIRS4;

import java.util.List;

/**
 *
 * @author rjd chatgtp
 */

// Here's a basic implementation of CRC16 in Java. This code uses the common CRC16-CCITT algorithm, which should work if that's the specific CRC16 algorithm you're using. If you're using a different CRC16 variant (like CRC16-XMODEM or CRC16-MODBUS), the polynomial and initialization values may vary, and we can modify the code accordingly.


public class CRC16 {

    private static final int POLYNOMIAL = 0x1021;  // CRC-CCITT polynomial
    private static final int INITIAL_VALUE = 0xFFFF;  // Initial value for CRC calculation

    public static int init() {
        return INITIAL_VALUE;
    }

    public static int update(int crc, List<Byte> data) {
        for (Byte b : data) {
            crc ^= (b & 0xFF) << 8;  // XOR the byte into the high byte of crc
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;  // Keep it 16-bit
            }
        }
        return crc;
    }

    public static int finalize(int crc) {
        return crc & 0xFFFF;  // Return the CRC value as 16-bit
    }
}
