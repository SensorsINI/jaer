package com.inilabs.jaer.hardware.USBCAN;

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
//package com.inilabs.jaer.projects.inix.hardware.USBCAN;

/**
 *
 * @author rjd
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class USBCANH {

   
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

        public int getValue() 
            return value;
        }
    }

    public static final int FRAME_LEN = 8;

    public static class CanTunnel {
        private Object dev;
        private String canName;
        private int devType;
        private int devIndex;
        private int canIndex;
        private List<Byte> readContent;
        private Queue<List<Byte>> recvQueueHandle;
        private boolean isRunning;
        private List<Integer> recvIdList;

        public CanTunnel(Object canDev, int canIndex) {
            this.dev = canDev;
            this.canIndex = canIndex;
            this.recvQueueHandle = new ConcurrentLinkedQueue<>();
            this.recvIdList = new ArrayList<>();
        }

        public Status initCan() {
            // Implementation goes here
            return Status.OK;
        }

        public Status setRecvIdList(List<Integer> recvIdList) {
            this.recvIdList = recvIdList;
            return Status.OK;
        }

        public Status clearBuffer() {
            recvQueueHandle.clear();
            return Status.OK;
        }

        public Status startCan() {
            // Implementation to start CAN communication
            return Status.OK;
        }

        public int getReceiveNum() {
            return recvQueueHandle.size();
        }

        public Status resetCan() {
            // Reset the CAN device
            return Status.OK;
        }

        public List<List<Byte>> recvData(int length, int waitTime) {
            // Placeholder for receiving data
            return new ArrayList<>();
        }

        public int sendData(int canId, List<Byte> data) {
            // Placeholder for sending data
            return 0;
        }

        public int readErrInfo() {
            // Placeholder for reading error information
            return 0;
        }

        public void pushDataToRecvQueue(List<Byte> data) {
            recvQueueHandle.offer(data);
        }

        public List<Byte> popDataFromRecvQueue() {
            return recvQueueHandle.poll();
        }
    }

    public static class USBCAN_II extends CanTunnel {
        public USBCAN_II(Object canDev, int canIndex) {
            super(canDev, canIndex);
        }

        @Override
        public Status initCan() {
            // Initialization for USBCAN_II
            return super.initCan();
        }

        public Object getInitCfgById() {
            // Return configuration data
            return null;
        }
    }

    public static class CanDev {
        private String name;
        private int devType;
        private int devIndex;
        private int canIndex;
        private boolean isOpen;
        private CanTunnel tunnel;

        public CanDev(String name, TunnelType devType, int devIndex, int canIndex) {
            this.name = name;
            this.devType = devType.getValue();
            this.devIndex = devIndex;
            this.canIndex = canIndex;
            this.tunnel = new USBCAN_II(null, canIndex);
            this.isOpen = false;
        }

        public boolean isOpen() {
            return isOpen;
        }

        public Status close() {
            isOpen = false;
            return Status.OK;
        }

        public String getName() {
            return name;
        }

        public int getDevType() {
            return devType;
        }

        public int getDevIndex() {
            return devIndex;
        }

        public CanTunnel getTunnel() {
            return tunnel;
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
            stopped = false;
            thread = new Thread(this::run);
            thread.start();
        }

        public void stop() {
            stopped = true;
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void run() {
            while (!stopped) {
                // Receive CAN data
            }
        }
    }

    public static class CANConnection {
        private boolean isConnected;
        private CanDev device;
        private CanTunnel tunnel;
        private RecvCanData recvThread;
        private int sendId;
        private boolean stopped = false;

        public CANConnection(int sendId, int recvId, String canName, TunnelType tunnelType, int tunnelId, int canIndex) {
            this.device = new CanDev(canName, tunnelType, tunnelId, canIndex);
            this.tunnel = device.getTunnel();
            this.recvThread = new RecvCanData(tunnel);
            this.isConnected = false;
        }

        public boolean getConnectionStatus() {
            return isConnected;
        }

        public void setSendId(int sendId) {
            this.sendId = sendId;
        }

        public List<String> popRecvCmd(String key) {
            // Implement logic for receiving command
            return new ArrayList<>();
        }

        public List<String> getRecvCmd(String key) {
            // Implement logic for getting received command
            return new ArrayList<>();
        }

        public int sendCmd(List<Byte> cmd) {
            return tunnel.sendData(sendId, cmd);
        }

        public CanTunnel getTunnel() {
            return tunnel;
        }
    }
}
