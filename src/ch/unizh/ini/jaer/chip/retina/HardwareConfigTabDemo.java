package ch.unizh.ini.jaer.chip.retina;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Headless checks that Hardware Configuration opens on the user-friendly tab.
 * Run after {@code ant compile}:
 * {@code java -cp build/classes;jars/*;lib/* ch.unizh.ini.jaer.chip.retina.HardwareConfigTabDemo}
 */
public final class HardwareConfigTabDemo {

    private static int assertions;

    private HardwareConfigTabDemo() {
    }

    public static void main(String[] args) throws Exception {
        testHtmlUserFriendlyTitle();
        testBasicControlsTitle();
        testSelectsUserFriendlyNotChipConfig();
        testNestedPaneSearch();
        testDavisConfigDoesNotRestoreSavedIndex();
        testDavis346blueXmlOpensOnFirstTab();
        testBiasgenFrameSelectsUserFriendlyTab();
        System.out.println("HW_CONFIG_TAB ASSERTIONS=" + assertions);
        System.out.println("HW_CONFIG_TAB PASS");
    }

    private static void testHtmlUserFriendlyTitle() {
        require(DVSUserControlPanel.isUserFriendlyTabTitle(
                "<html><strong><font color=\"red\">User-Friendly Controls"),
                "Davis HTML tab title is user-friendly");
        require(!DVSUserControlPanel.isUserFriendlyTabTitle("Chip Config"),
                "Chip Config is not user-friendly");
        require(!DVSUserControlPanel.isUserFriendlyTabTitle("DVS Auto Controller"),
                "DVS Auto Controller is not the user-friendly tab");
        require(!DVSUserControlPanel.isUserFriendlyTabTitle("Bias Current Config"),
                "Bias Current Config is not user-friendly");
    }

    private static void testBasicControlsTitle() {
        require(DVSUserControlPanel.isUserFriendlyTabTitle("Basic controls"),
                "DVS128/Cochlea Basic controls is user-friendly");
        require(!DVSUserControlPanel.isUserFriendlyTabTitle("Expert controls"),
                "Expert controls is not user-friendly");
    }

    private static void testSelectsUserFriendlyNotChipConfig() {
        JTabbedPane tabs = davisLikeTabs();
        tabs.setSelectedIndex(8);
        require(tabs.getSelectedIndex() == 8, "precondition: Chip Config selected");
        int selected = DVSUserControlPanel.selectUserFriendlyTab(tabs);
        require(selected == 0, "selectUserFriendlyTab returns index 0, was " + selected);
        require(tabs.getSelectedIndex() == 0, "User-Friendly Controls is selected");
        require(tabs.getTitleAt(tabs.getSelectedIndex()).contains("User-Friendly"),
                "selected title is User-Friendly Controls");
    }

    private static void testNestedPaneSearch() {
        JPanel root = new JPanel();
        JTabbedPane tabs = davisLikeTabs();
        tabs.setSelectedIndex(8);
        root.add(tabs);
        require(DVSUserControlPanel.selectUserFriendlyTabIn(root),
                "nested search finds the Davis tabbed pane");
        require(tabs.getSelectedIndex() == 0, "nested search selects User-Friendly");
    }

    private static void testDavisConfigDoesNotRestoreSavedIndex() throws Exception {
        String src = Files.readString(davisConfigPath(), StandardCharsets.UTF_8);
        require(src.contains("DVSUserControlPanel.selectUserFriendlyTab(configTabbedPane)"),
                "DavisConfig selects the user-friendly tab");
        require(!src.contains("bgTabbedPaneSelectedIndex\", 0)"),
                "DavisConfig no longer restores saved tab index on open");
    }

    private static void testDavis346blueXmlOpensOnFirstTab() throws Exception {
        String xml = Files.readString(Paths.get("deviceSettings", "Davis346", "Davis346blue.xml"),
                StandardCharsets.UTF_8);
        require(xml.contains("DavisBaseCamera.bgTabbedPaneSelectedIndex\" value=\"0\""),
                "Davis346blue.xml first-use import selects tab 0");
        require(!xml.contains("DavisBaseCamera.bgTabbedPaneSelectedIndex\" value=\"8\""),
                "Davis346blue.xml no longer stores Chip Config tab index 8");
    }

    private static void testBiasgenFrameSelectsUserFriendlyTab() throws Exception {
        String src = Files.readString(Paths.get("src", "net", "sf", "jaer", "biasgen", "BiasgenFrame.java"),
                StandardCharsets.UTF_8);
        require(src.contains("selectUserFriendlyTabIn(getContentPane())"),
                "BiasgenFrame selects the user-friendly tab after building the panel");
    }

    private static JTabbedPane davisLikeTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("<html><strong><font color=\"red\">User-Friendly Controls", new JPanel());
        tabs.addTab("DVS Auto Controller", new JPanel());
        tabs.addTab("Bias Current Config", new JPanel());
        tabs.addTab("Multiplexer Config", new JPanel());
        tabs.addTab("DVS Config", new JPanel());
        tabs.addTab("APS Config", new JPanel());
        tabs.addTab("IMU Config", new JPanel());
        tabs.addTab("External Input Config", new JPanel());
        tabs.addTab("Chip Config", new JPanel());
        tabs.addTab("APS Autoexposure Control", new JPanel());
        tabs.addTab("Video Control", new JPanel());
        return tabs;
    }

    private static Path davisConfigPath() {
        return Paths.get("src", "eu", "seebetter", "ini", "chips", "davis", "DavisConfig.java");
    }

    private static void require(boolean cond, String msg) {
        assertions++;
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
