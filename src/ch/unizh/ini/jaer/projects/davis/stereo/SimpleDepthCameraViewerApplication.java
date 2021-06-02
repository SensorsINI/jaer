package ch.unizh.ini.jaer.projects.davis.stereo;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openni.*;

public class SimpleDepthCameraViewerApplication extends Thread implements ItemListener {

    private JFrame mFrame;
    private JPanel mPanel;
    private SimpleDepthCameraViewer mViewer;
    private boolean mShouldRun = true;
    private Device mDevice;
    private VideoStream mVideoStream;
    private ArrayList<SensorType> mDeviceSensors;
    private ArrayList<VideoMode> mSupportedModes;

    private JComboBox mComboBoxStreams;
    private JComboBox mComboBoxVideoModes;

    Recorder mRecorder;

    private boolean recording = false;

    public SimpleDepthCameraViewerApplication(Device device) {
        mDevice = device;

        mFrame = new JFrame("OpenNI Simple Viewer");
        mPanel = new JPanel();
        mViewer = new SimpleDepthCameraViewer();

        // register to key events
        mFrame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent arg0) {
            }

            @Override
            public void keyReleased(KeyEvent arg0) {
            }

            @Override
            public void keyPressed(KeyEvent arg0) {
                if (arg0.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    mShouldRun = false;
                }
            }
        });

        // register to closing event
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                mShouldRun = false;
            }
        });

        mComboBoxStreams = new JComboBox();
        mComboBoxVideoModes = new JComboBox();

        mComboBoxStreams.addItem("<Stream Type>");
        mDeviceSensors = new ArrayList<SensorType>();

        if (device.getSensorInfo(SensorType.COLOR) != null) {
            mDeviceSensors.add(SensorType.COLOR);
            mComboBoxStreams.addItem("Color");
        }

        if (device.getSensorInfo(SensorType.DEPTH) != null) {
            mDeviceSensors.add(SensorType.DEPTH);
            mComboBoxStreams.addItem("Depth");
        }

        mComboBoxStreams.addItemListener(this);
        mComboBoxVideoModes.addItemListener(this);
        mViewer.setSize(800, 600);

        mPanel.add("West", mComboBoxStreams);
        mPanel.add("East", mComboBoxVideoModes);
        mFrame.add("North", mPanel);
        mFrame.add("Center", mViewer);
        mFrame.setSize(mViewer.getWidth() + 20, mViewer.getHeight() + 80);
        mFrame.setVisible(true);

        //recorder object
        mRecorder = new Recorder();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.DESELECTED) {
            return;
        }

        if (e.getSource() == mComboBoxStreams) {
            selectedStreamChanged();
        } else if (e.getSource() == mComboBoxVideoModes) {
            selectedVideoModeChanged();
        }
    }

    void selectedStreamChanged() {

        if (mVideoStream != null) {
            mVideoStream.stop();
            mViewer.setStream(null);
            mVideoStream.destroy();
            mVideoStream = null;
        }

        int sensorIndex = mComboBoxStreams.getSelectedIndex() - 1;
        if (sensorIndex == -1) {
            return;
        }

        SensorType type = mDeviceSensors.get(sensorIndex);

        mVideoStream = VideoStream.create(mDevice, type);
        List<VideoMode> supportedModes = mVideoStream.getSensorInfo().getSupportedVideoModes();
        mSupportedModes = new ArrayList<VideoMode>();

        // now only keeo the ones that our application supports
        for (VideoMode mode : supportedModes) {
            switch (mode.getPixelFormat()) {
                case DEPTH_1_MM:
                case DEPTH_100_UM:
                case SHIFT_9_2:
                case SHIFT_9_3:
                case RGB888:
                    mSupportedModes.add(mode);
                    break;
            }
        }

        // and add them to combo box
        mComboBoxVideoModes.removeAllItems();
        mComboBoxVideoModes.addItem("<Video Mode>");

        for (VideoMode mode : mSupportedModes) {
            mComboBoxVideoModes.addItem(String.format(
                    "%d x %d @ %d FPS (%s)",
                    mode.getResolutionX(),
                    mode.getResolutionY(),
                    mode.getFps(),
                    pixelFormatToName(mode.getPixelFormat())));
        }
    }

    private String pixelFormatToName(PixelFormat format) {
        switch (format) {
            case DEPTH_1_MM:
                return "1 mm";
            case DEPTH_100_UM:
                return "100 um";
            case SHIFT_9_2:
                return "9.2";
            case SHIFT_9_3:
                return "9.3";
            case RGB888:
                return "RGB";
            default:
                return "UNKNOWN";
        }
    }

    void selectedVideoModeChanged() {
        mVideoStream.stop();

        int modeIndex = mComboBoxVideoModes.getSelectedIndex() - 1;
        if (modeIndex == -1) {
            return;
        }

        VideoMode mode = mSupportedModes.get(modeIndex);
        mVideoStream.setVideoMode(mode);
        mViewer.setStream(mVideoStream);
        mViewer.setSize(mode.getResolutionX(), mode.getResolutionY());
        mFrame.setSize(mViewer.getWidth() + 20, mViewer.getHeight() + 80);
        mVideoStream.start();
    }

    public void run() {
        while (mShouldRun) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mFrame.dispose();
    }

    public boolean isFrameCaptureRunning() {
        if (mViewer.mLastFrame != null) {
            return true;
        } else {
            return false;
        }
    }

    public void saveLastImage(String path, String suffix) {
        String file = path + "\\" + mDevice.getDeviceInfo().getName().toLowerCase() + "_" + mVideoStream.getSensorType().name().toLowerCase() + suffix;
        mViewer.saveFrame(file);
    }

    public File startRecording(String path) {
        if (!recording) {
            if (mDevice != null && mVideoStream != null) {
                recording = true;
                String file = path + "\\" + mDevice.getDeviceInfo().getName().toLowerCase() + "_" + mVideoStream.getSensorType().name().toLowerCase()+".oni";
                mRecorder = Recorder.create(file);
                File depthFile = new File(file);
                mRecorder.addStream(mVideoStream, false);
                mRecorder.start();
                return depthFile;
            }
            return null;
        }
        return null;
    }

    public void stopRecording() {
        if (recording) {
            recording = false;
            mRecorder.stop();
            mRecorder.destroy();
        }
    }
}
