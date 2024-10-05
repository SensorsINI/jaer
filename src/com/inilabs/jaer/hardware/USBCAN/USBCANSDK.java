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
package com.inilabs.jaer.hardware.USBCAN;

/**
 * Java implementation of 
 * https://github.com/ConstantRobotics/USBCAN_SDK/tree/c07d35fdf4837106716961dbbecc30d5bc169ea1
 *
 * Key Changes:
 *
 *    Pointers & Manual Memory Management: Java doesn't use pointers or manual memory management, so new and delete have been replaced with Java object instantiation and automatic garbage collection.
 *    C++ Style Enums: Replaced C++ enums with Java enums, ensuring proper mapping between enum values and functionality.
 *    Concurrency: Java's Thread class is used in place of std::thread, and synchronized or ConcurrentLinkedQueue ensures thread-safe operations.
 *    Exception Handling: Java uses exceptions, so any operations with potential errors (e.g., receiving data) are wrapped in try-catch blocks to handle exceptions gracefully.
 *    Device Interaction: In place of C++ device interaction functions (OpenDevice, StartCAN, etc.), helper functions were added to simulate CAN communication, as they were hardware-specific in the C++ code.
 * 
 * 
 * 
 * @author rjd chatgtp
 */


import java.util.*;
import java.util.concurrent.*;
//import java.util.concurrent.locks.*;


public class USBCANSDK {
    
    
    public enum Status {
        OK(1),
        ERR(0);

        private int value;

        Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    
      public enum SendType {
        NORMAL_SEND(0),
        SINGLE_SEND(1);

        private int value;

        SendType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RemoteFlag {
        DATA_FRAME(0),
        REMOTE_FRAME(1);

        private int value;

        RemoteFlag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum ExternFlag {
        STD_FRAME(0),
        EXT_FRAME(1);

        private int value;

        ExternFlag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RefType {
        SET_BAUDRATE_CMD(0),
        SET_FILTER_CMD(1),
        START_FILTER_CMD(2),
        CLEAR_FILTER_CMD(3),
        SET_OVER_TIME(4),
        SET_AUTO_SEND(5),
        CLEAR_AUTO_SEND(6);

        private int value;

        RefType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum TunnelType {
        USBCAN_II_TYPE(4),
        USBCAN_2E_U_TYPE(21);

        private int value;

        TunnelType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }


        
        
    public static class CanDev {
        private String name;
        private int devType;
        private int devIndex;
        private int canIndex;
        private boolean isOpen;
        private CanTunnel tunnel;

        public CanDev(String pdcanName, TunnelType devType, int devIndex, int canIndex) {
            this.name = pdcanName;
            this.devType = devType.getValue();
            this.devIndex = devIndex;
            this.canIndex = canIndex;
            this.isOpen = false;

           
            Status ret = Status.ERR;
            if (pdcanName.equals("GC_USBCAN")) {
                for (int i = 0; i < 3; i++) {
                    ret = openDevice(this.devType, this.devIndex, 0);
                    if (ret == Status.OK) {
                        this.isOpen = true;
                        break;
                    }
                }

                this.tunnel = getCanDev(this.devType, this.canIndex);
            }
        }

        private USBCAN_II getCanDev(int devType, int canIndex) {
            if (this.devType == devType) {
                return new USBCAN_II(this, canIndex);
            } else {
                return null;
            }
        }

        public String getName() {
            return this.name;
        }

        public int getDevType() {
            return this.devType;
        }

        public int getDevIndex() {
            return this.devIndex;
        }

        public CanTunnel getTunnel() {
            return this.tunnel;
        }

        public boolean isOpen() {
            return this.isOpen;
        }

        public Status close() {
            if (this.isOpen) {
                return closeDevice(this.devType, this.devIndex);
            } else {
                return Status.ERR;
            }
        }
    }

   
    public static class CanTunnel {
        private Object dev;
        private String canName;
        private int devType;
        private int devIndex;
        private int canIndex;
        private List<Byte> readContent;
        private Queue<List<Byte>> recvQueueHandle;
        private List<Integer> recvIdList;
        private boolean isRunning;

        public CanTunnel(Object canDev, int canIndex) {
            this.dev = canDev;
            this.devType = ((CanDev) this.dev).getDevType();
            this.devIndex = ((CanDev) this.dev).getDevIndex();
            this.canName = ((CanDev) this.dev).getName();
            this.canIndex = canIndex;

            this.readContent = new ArrayList<>();
            this.recvQueueHandle = new ConcurrentLinkedQueue<>();
            this.recvIdList = new ArrayList<>();
            this.isRunning = false;
        }

        public Status initCan() {
            return Status.OK;
        }

        public Status setRecvIdList(List<Integer> recvIdList) {
            this.recvIdList = recvIdList;
            return Status.OK;
        }

        public Status clearBuffer() {
            return clearBuffer(this.devType, this.devIndex, this.canIndex);
        }

        public Status startCan() {
            Status ret = Status.ERR;
            if (((CanDev) this.dev).isOpen()) {
                ret = startCAN(this.devType, this.devIndex, this.canIndex);
                if (ret == Status.OK) {
                    this.isRunning = true;
                }
            }
            return ret;
        }

        public int getReceiveNum() {
            if (this.isRunning) {
                return getReceiveNum(this.devType, this.devIndex, this.canIndex);
            } else {
                return 0;
            }
        }

        public Status resetCan() {
            if (this.isRunning) {
                return resetCAN(this.devType, this.devIndex, this.canIndex);
            } else {
                return Status.ERR;
            }
        }

        public List<List<Byte>> recvData(int length, int waitTime) {
            if (this.isRunning) {
                List<List<Byte>> dataList = new ArrayList<>();
                try {
                    CAN_OBJ[] recvBuf = new CAN_OBJ[length];
                    int recvLen = receive(this.devType, this.devIndex, this.canIndex, recvBuf, length, waitTime);
                    for (int i = 0; i < recvLen; i++) {
                        List<Byte> canData = new ArrayList<>();
                        for (int id : this.recvIdList) {
                            if (recvBuf[i].ID == id) {
                                for (int n = 0; n < recvBuf[i].DataLen; n++) {
                                    canData.add(recvBuf[i].Data[n]);
                                }
                            }
                        }
                        if (!canData.isEmpty()) {
                            dataList.add(canData);
                        }
                    }
                    return dataList;
                } catch (Exception e) {
                    e.printStackTrace();
                    return new ArrayList<>();
                }
            } else {
                return new ArrayList<>();
            }
        }

        public int sendData(int canId, List<Byte> data) {
            if (this.isRunning) {
                int dataLen = data.size();
                int fullFrameNum = dataLen / FRAME_LEN;
                int leftLen = dataLen % FRAME_LEN;
                int frameNum = (leftLen == 0) ? fullFrameNum : fullFrameNum + 1;

                CAN_OBJ[] sendBuf = new CAN_OBJ[frameNum];
                int dataOffset = 0;

                for (int i = 0; i < fullFrameNum; i++) {
                    sendBuf[i] = createCanObj(canId, data.subList(dataOffset, dataOffset + FRAME_LEN));
                    dataOffset += FRAME_LEN;
                }

                if (leftLen > 0) {
                    sendBuf[frameNum - 1] = createCanObj(canId, data.subList(dataOffset, dataOffset + leftLen));
                }

                int sendLen = transmit(this.devType, this.devIndex, this.canIndex, sendBuf, frameNum);

                return (sendLen == frameNum) ? sendLen : 0;
            } else {
                return 0;
            }
        }

        
        
        
        public int readErrInfo() {
            ERR_INFO errInfo = new ERR_INFO();
            Status ret = readErrInfo(this.devType, this.devIndex, this.canIndex, errInfo);
            if (ret == Status.OK) {
                return errInfo.ErrCode;
            } else {
                return 0;
            }
        }

        public void pushDataToRecvQueue(List<Byte> data) {
            this.recvQueueHandle.offer(data);
        }

        public List<Byte> popDataFromRecvQueue() {
            return this.recvQueueHandle.poll();
        }
    }

    public static class USBCAN_II extends CanTunnel {
        public USBCAN_II(Object canDev, int canIndex) {
            super(canDev, canIndex);
        }

        @Override
        public Status initCan() {
            INIT_CONFIG initConfig = (INIT_CONFIG) getInitCfgById();
            return initCAN(this.devType, this.devIndex, this.canIndex, initConfig);
        }

        public Object getInitCfgById() {
            INIT_CONFIG initConfig = new INIT_CONFIG();
            initConfig.AccCode = 0x00000000;
            initConfig.AccMask = 0xFFFFFFFF;
            initConfig.Reserved = 0;
            initConfig.Filter = 1;
            initConfig.Timing0 = 0x00;
            initConfig.Timing1 = 0x14;
            initConfig.Mode = 0;
            return initConfig;
        }
    }

    public static class RecvCanData {
        private Thread thread;
        private boolean stopped = false;
        private CanTunnel dev;

        public RecvCanData(CanTunnel dev) {
            this.dev = dev;
        }

        public void start() {
            this.stopped = false;
            this.thread = new Thread(this::run);
            this.thread.start();
        }

        private void run() {
            while (!stopped) {
                int num = dev.getReceiveNum();
                if (num > 0) {
                    List<List<Byte>> recvData = dev.recvData(num, 400);
                    for (List<Byte> data : recvData) {
                        dev.pushDataToRecvQueue(data);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stop() {
            this.stopped = true;
            try {
                this.thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static class CANConnection {
        private boolean isConnected;
        private CanDev device;
        private CanTunnel tunnel;
        private RecvCanData recvThread;
        private int sendId;
        private boolean stopped;

        public CANConnection(int sendId, int recvId, String canName, TunnelType tunnelType, int tunnelId, int canIndex) {
            this.device = new CanDev(canName, tunnelType, tunnelId, canIndex);
            this.tunnel = this.device.getTunnel();
            List<Integer> idList = new ArrayList<>();
            idList.add(recvId);
            this.tunnel.setRecvIdList(idList);
            setSendId(sendId);
            this.stopped = false;

            Status ret = initCan();
            if (ret == Status.OK) {
                ret = startCan();
                if (ret == Status.OK) {
                    listenThread();
                }
            }
            this.isConnected = (ret == Status.OK);
        }

        public boolean getConnectionStatus() {
            return this.isConnected;
        }

        public void setSendId(int sendId) {
            this.sendId = sendId;
        }

        public List<String> popRecvCmd(String key) {
            List<String> recvCmd = new ArrayList<>();
            for (Map.Entry<String, String> entry : this.tunnel.recvQueue.entrySet()) {
                if (entry.getKey().equals(key)) {
                    recvCmd.add(entry.getValue());
                    this.tunnel.recvQueue.remove(entry.getKey());
                }
            }
            return recvCmd;
        }

        public List<String> getRecvCmd(String key) {
            List<String> recvCmd = new ArrayList<>();
            for (Map.Entry<String, String> entry : this.tunnel.recvQueue.entrySet()) {
                if (entry.getKey().equals(key)) {
                    recvCmd.add(entry.getValue());
                }
            }
            return recvCmd;
        }

        public int sendCmd(List<Byte> cmd) {
            return this.tunnel.sendData(this.sendId, cmd);
        }

        public CanTunnel getTunnel() {
            return this.tunnel;
        }

        private Status initCan() {
            return this.tunnel.initCan();
        }

        private Status startCan() {
            this.tunnel.clearBuffer();
            return this.tunnel.startCan();
        }

        private void listenThread() {
            this.recvThread = new RecvCanData(this.tunnel);
            this.recvThread.start();
        }

        public void stop() {
            this.stopped = true;
        }
    }

    // Helper functions for CAN operations would go here
    private static Status openDevice(int devType, int devIndex, int reserved) {
        // Open CAN device logic
        return Status.OK;
    }

    private static Status closeDevice(int devType, int devIndex) {
        // Close CAN device logic
        return Status.OK;
    }

    private static int receive(int devType, int devIndex, int canIndex, CAN_OBJ[] recvBuf, int length, int waitTime) {
        // Receive CAN data logic
        return length; // Example return
    }

    private static int transmit(int devType, int devIndex, int canIndex, CAN_OBJ[] sendBuf, int frameNum) {
        // Transmit CAN data logic
        return frameNum; // Example return
    }

    private static Status startCAN(int devType, int devIndex, int canIndex) {
        // Start CAN logic
        return Status.OK;
    }

    private static Status resetCAN(int devType, int devIndex, int canIndex) {
        // Reset CAN logic
        return Status.OK;
    }

    private static int getReceiveNum(int devType, int devIndex, int canIndex) {
        // Get number of received CAN messages
        return 0; // Example return
    }

    private static Status clearBuffer(int devType, int devIndex, int canIndex) {
        // Clear CAN buffer
        return Status.OK;
    }

    private static Status readErrInfo(int devType, int devIndex, int canIndex, ERR_INFO errInfo) {
        // Read CAN error information
        return Status.OK;
    }

   private static CAN_OBJ createCanObj(int canId, List<Byte> data) {
        CAN_OBJ canObj = new CAN_OBJ();
        canObj.ID = canId;
        canObj.SendType = SendType.NORMAL_SEND.getValue();
        canObj.RemoteFlag = RemoteFlag.DATA_FRAME.getValue();
        canObj.ExternFlag = ExternFlag.STD_FRAME.getValue();
        canObj.DataLen = data.size();
        for (int i = 0; i < data.size(); i++) {
            canObj.Data[i] = data.get(i);
        }
        return canObj;

}
  