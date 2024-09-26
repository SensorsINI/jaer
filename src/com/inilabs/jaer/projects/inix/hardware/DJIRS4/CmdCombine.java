package com.inilabs.jaer.projects.inix.hardware.DJIRS4;

import com.google.common.primitives.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
// Translated by chatGTP
//Key changes:

    //Converted the C++ vector to a List<Byte> in Java.
    //Mapped C++ data types like uint8_t to Java's byte.
    //Constructors and destructors are translated accordingly (CmdCombine constructor and finalize method).
    //Added placeholder methods for CRC calculations (computeCRC16 and computeCRC32) since the actual functions are not provided.



   
      
  /*
         * DJI R SDK Protocol Description
         *
         * 2.1 Data Format
         * +----------------------------------------------+------+------+------+
         * |                     PREFIX                   | CRC  | DATA | CRC  |
         * |------+----------+-------+------+------+------+------+------+------|
         * |SOF   |Ver/Length|CmdType|ENC   |RES   |SEQ   |CRC-16|DATA  |CRC-32|
         * |------|----------|-------|------|------|------|------|------|------|
         * |1-byte|2-byte    |1-byte |1-byte|3-byte|2-byte|2-byte|n-byte|4-byte|
         * +------+----------+-------+------+------+------+------+------+------+
         *
         * 2.2 Data Segment (field DATA in 2.1 Data Format)
         * +---------------------+
         * |           DATA      |
         * |------+------+-------|
         * |CmdSet|CmdID |CmdData|
         * |------|------|-------|
         * |1-byte|1-byte|n-byte |
         * +------+------+-------+
         */
  
public class CmdCombine {

    public CmdCombine() {
        // Constructor
    }

    public void finalize() {
        // Destructor equivalent
    }

    
    public List<Byte> combine(byte cmdType, byte cmdSet, byte cmdId, List<Byte> data) {
        int prefixLength = 10;
        int crc16Length = 2;
        int dataLength = 2 + data.size();
        int crc32Length = 4;
        int cmdLength = prefixLength + crc16Length + dataLength + crc32Length;
        List<Byte> seqNum = seqNum();

        List<Byte> cmd = new ArrayList<>(cmdLength);
        cmd.add((byte) 0xAA);  // Start of frame (SOF)
        cmd.add((byte) (cmdLength & 0xFF)); // Command length LSB
        cmd.add((byte) ((cmdLength >> 8) & 0xFF)); // Command length MSB
        cmd.add(cmdType);
        cmd.add((byte) 0x00); // Encryption (not used)
        cmd.add((byte) 0x00); // Reserved 1
        cmd.add((byte) 0x00); // Reserved 2
        cmd.add((byte) 0x00); // Reserved 3
        cmd.add(seqNum.get(0)); // Sequence number byte 1
        cmd.add(seqNum.get(1)); // Sequence number byte 2

        // CRC16 calculation
        int crc16 = CRC16.init();
        crc16 = CRC16.update(crc16, cmd.subList(0, cmd.size()));  // Compute CRC16 up to current position
        crc16 = CRC16.finalize(crc16);

        // Append CRC16 to command
        cmd.add((byte) (crc16 & 0xFF)); // CRC16 LSB
        cmd.add((byte) ((crc16 >> 8) & 0xFF)); // CRC16 MSB

        // Add command set, command ID, and data
        cmd.add(cmdSet);  // Command set
        cmd.add(cmdId);   // Command ID
        cmd.addAll(data); // Data payload

        // CRC32 calculation
        int crc32 = CRC32.init();
        crc32 = CRC32.update(crc32, cmd);  // Compute CRC32 on the entire command list
        crc32 = CRC32.finalize(crc32);

        // Append CRC32 to command
        cmd.add((byte) (crc32 & 0xFF));     // CRC32 byte 3
        cmd.add((byte) ((crc32 >> 8) & 0xFF));  // CRC32 byte 2
        cmd.add((byte) ((crc32 >> 16) & 0xFF)); // CRC32 byte 1
        cmd.add((byte) ((crc32 >> 24) & 0xFF)); // CRC32 byte 0

        return cmd;
    }

    private List<Byte> seqNum() {
        short Seq_Init_Data = 0x2210;
        if (Seq_Init_Data >= 0xFFFD)
            Seq_Init_Data = 0x0002;
        Seq_Init_Data += 1;

        List<Byte> ret = new ArrayList<>();
        ret.add((byte) ((Seq_Init_Data >> 8) & 0xFF)); // MSB of sequence number
        ret.add((byte) (Seq_Init_Data & 0xFF));        // LSB of sequence number
        return ret;
    }
}
