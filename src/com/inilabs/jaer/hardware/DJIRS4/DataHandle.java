package com.inilabs.jaer.hardware.DJIRS4;

/**
 *
 * @author rjd chatgtp
 * 
 */

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.TimeUnit;

public class DataHandle {
    private Thread thread;
    private boolean stopped = false;
    private Object dev;
    private List<String> rsps;
    private Lock rdcontentLock;
    private Lock inputPositionLock;
    private Condition inputPositionCondVar;
    private boolean inputPositionReadyFlag;
    private short yaw, roll, pitch;  // Java does not have unsigned types, so use short for int16_t
    private List<List<Byte>> cmdList;

    public DataHandle(Object dev) {
        this.dev = dev;
        this.rsps = new ArrayList<>();
        this.rdcontentLock = new ReentrantLock();
        this.inputPositionLock = new ReentrantLock();
        this.inputPositionCondVar = inputPositionLock.newCondition();
        this.cmdList = new ArrayList<>();
        this.inputPositionReadyFlag = false;
    }

    public void start() {
        stopped = false;
        thread = new Thread(() -> run());
        thread.start();
    }

    public void stop() {
        stopped = true;
        try {
            if (thread != null) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addCmd(List<Byte> cmd) {
        rdcontentLock.lock();
        try {
            cmdList.add(cmd);
            if (cmdList.size() > 10) {
                cmdList.remove(0);
            }
        } finally {
            rdcontentLock.unlock();
        }
    }

    public boolean getPosition(short[] yaw, short[] roll, short[] pitch, int timeoutMs) {
        inputPositionLock.lock();
        try {
            inputPositionReadyFlag = false;
            boolean positionAvailable = inputPositionCondVar.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (positionAvailable) {
                yaw[0] = this.yaw;
                roll[0] = this.roll;
                pitch[0] = this.pitch;
            }
            return positionAvailable;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            inputPositionLock.unlock();
        }
    }

    private void run() {
        List<Byte> v1PackList = new ArrayList<>();
        int packLen = 0;
        int step = 0;
        CANConnection devConnection = (CANConnection) dev;

        while (!stopped) {
            List<Byte> frame = devConnection.getTunnel().popDataFromRecvQueue();
            for (int i = 0; i < frame.size(); i++) {
                switch (step) {
                    case 0:
                        if (frame.get(i) == (byte) 0xAA) {
                            v1PackList.add(frame.get(i));
                            step = 1;
                        }
                        break;
                    case 1:
                        packLen = frame.get(i);
                        v1PackList.add(frame.get(i));
                        step = 2;
                        break;
                    case 2:
                        packLen |= ((frame.get(i) & 0x03) << 8);
                        v1PackList.add(frame.get(i));
                        step = 3;
                        break;
                    case 3:
                        v1PackList.add(frame.get(i));
                        if (v1PackList.size() == 12) {
                            if (checkHeadCRC(v1PackList)) {
                                step = 4;
                            } else {
                                step = 0;
                                v1PackList.clear();
                            }
                        }
                        break;
                    case 4:
                        v1PackList.add(frame.get(i));
                        if (v1PackList.size() == packLen) {
                            step = 0;
                            if (checkPackCRC(v1PackList)) {
                                processCmd(v1PackList);
                            }
                            v1PackList.clear();
                        }
                        break;
                    default:
                        step = 0;
                        v1PackList.clear();
                        break;
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void processCmd(List<Byte> data) {
        byte cmdType = data.get(3);
        boolean isOk = false;
        byte[] cmdKey = new byte[2];

        if (cmdType == 0x20) {
            rdcontentLock.lock();
            try {
                for (int i = 0; i < cmdList.size(); i++) {
                    List<Byte> cmd = cmdList.get(i);
                    if (cmd.size() >= 10) {
                        short lastCmdCrc = (short) ((cmd.get(8) & 0xFF) | (cmd.get(9) << 8));
                        short dataCrc = (short) ((data.get(8) & 0xFF) | (data.get(9) << 8));
                        if (lastCmdCrc == dataCrc) {
                            cmdKey[0] = cmd.get(12);
                            cmdKey[1] = cmd.get(13);
                            cmdList.remove(i);
                            isOk = true;
                            break;
                        }
                    }
                }
            } finally {
                rdcontentLock.unlock();
            }
        } else {
            cmdKey[0] = data.get(12);
            cmdKey[1] = data.get(13);
            isOk = true;
        }

        if (isOk) {
            int key = ((cmdKey[1] & 0xFF) << 8) | (cmdKey[0] & 0xFF);
            switch (key) {
                case 0x000E:
                    System.out.println("get posControl request");
                    break;
                case 0x020E:
                    yaw = (short) ((data.get(14) & 0xFF) | (data.get(15) << 8));
                    roll = (short) ((data.get(16) & 0xFF) | (data.get(17) << 8));
                    pitch = (short) ((data.get(18) & 0xFF) | (data.get(19) << 8));

                    inputPositionLock.lock();
                    try {
                        inputPositionReadyFlag = true;
                        inputPositionCondVar.signal();
                    } finally {
                        inputPositionLock.unlock();
                    }
                    break;
                default:
                    System.out.println("get unknown request");
                    break;
            }
        }
    }

    private boolean checkHeadCRC(List<Byte> data) {
        int crc16 = CRC16.init();
        crc16 = CRC16.update(crc16, data.subList(0, 10));  
        crc16 = CRC16.finalize(crc16);
        
        short recvCrc = (short) ((data.get(data.size() - 2) & 0xFF) | (data.get(data.size() - 1) << 8));

        return crc16 == recvCrc;
    }

    private boolean checkPackCRC(List<Byte> data) {
        int crc32 = CRC32.init();
        crc32 = CRC32.update(crc32, data.subList(0, data.size() - 4));
        crc32 = CRC32.finalize(crc32);

        int recvCrc = ((data.get(data.size() - 4) & 0xFF)) |
                      ((data.get(data.size() - 3) & 0xFF) << 8) |
                      ((data.get(data.size() - 2) & 0xFF) << 16) |
                      ((data.get(data.size() - 1) & 0xFF) << 24);

        return crc32 == recvCrc;
    }
}
