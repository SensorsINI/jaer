/*
 * Copyright (C) 2020 tobid.
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
package net.sf.jaer;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.util.StatusPrinter;
import com.install4j.api.context.UserCanceledException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.WordUtils;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;
import com.install4j.api.update.*;
import com.install4j.api.launcher.Variables;
import java.awt.HeadlessException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import javax.swing.ProgressMonitor;
import net.sf.jaer.util.MessageWithLink;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;

/**
 * Handles self update git version/tag check, git pull, and ant rebuild, via
 * JGit and ant.
 *
 * Also handles version check for install4j installed version of jAER.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdater {

    public static final boolean DEBUG = false; // false for production version,  true to clone here to tmp folders that do not overwrite our own .git
    private static Logger log = Logger.getLogger("JaerUpdater");
    private static Preferences prefs = Preferences.userNodeForPackage(JaerUpdater.class);
    public static String INSTALL4J_UPDATES_URL = "https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml";
//    private static AEViewerConsoleOutputFrame loggingWindow = null;

    public static void throwIoExceptionIfNoGit() throws IOException {
        File f = new File(".git");
        if (!f.exists()) {
            log.warning("folder .git does not exist at " + f.getAbsolutePath());
            throw new IOException("folder .git does not exist at " + f.getAbsolutePath());
        }
        try (Git git = Git.open(new File("."))) {
            log.info("successfully opened Git " + git.toString());
//            git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit
        } catch (Exception e) {
            log.warning("could not open Git repository " + f.getAbsolutePath() + ": caught " + e.toString());
            throw new IOException(e.toString());
        }
    }

    public static void checkForInstall4jReleaseUpdate(Component parent) {
        // check if rujning from installed version of jaer (fails if running from git compiled jaer)
        String currentVersion = "unknown";
        try {
            currentVersion = Variables.getCompilerVariable("sys.version");
        } catch (IOException e) {
            // TODO not running in installation
            JOptionPane.showMessageDialog(parent, "<html> Could not determine current version. <p>To check for udpates, you need to install jAER with an install4j installer. <p>(Probably are you running from git compiled development environment): <p>" + e.toString(), "Version check error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String updateUrl = INSTALL4J_UPDATES_URL;
        try {
            UpdateDescriptor updateDescriptor = UpdateChecker.getUpdateDescriptor(updateUrl, ApplicationDisplayMode.GUI);
            if (updateDescriptor.getPossibleUpdateEntry() != null) {
                // TODO an update is available, execute update downloader
                UpdateDescriptorEntry updateDescriptorEntry = updateDescriptor.getEntryForCurrentMediaFileId();
                String updateVersion = updateDescriptorEntry.getNewVersion();
                JOptionPane.showMessageDialog(parent,
                        new MessageWithLink("<html>Current version: " + currentVersion + "<p> Update " + updateVersion
                                + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>"),
                        "Update available", JOptionPane.INFORMATION_MESSAGE);
//                JOptionPane.showMessageDialog(parent, "<html>Update " + updateVersion + " is available; see <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>", "Releases update check", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent, new MessageWithLink("<html>No update available;<br> you are running current release " + currentVersion + "<p>See <a href=\"https://github.com/SensorsINI/jaer/releases\">jAER releases</a>"), "No update available", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | UserCanceledException e) {
            JOptionPane.showMessageDialog(parent, "Could not check for release update: " + e.toString(), "Update check error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Action listener that runs jAER ant build
     *
     * @param parent where progress dialog should be displayed over
     * @return the listener
     */
    public static ActionListener buildActionListener(Component parent) {
        UIManager.put("ProgressMonitor.progressText", "Build Progress");

        return (ActionEvent ae) -> {
            new Thread(() -> {
                log.info("Starting build");
                //creating ProgressMonitor instance
                javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Build Task",
                        "Build starting", 0, 100);
                final ProgressCounter progressCounter = new ProgressCounter("build", pm, 100000);

                //decide after 100 millis whether to show popup or not
                pm.setMillisToDecideToPopup(100);
                //after deciding if predicted time is longer than 100 show popup
                pm.setMillisToPopup(100);
                File buildFile = new File("build.xml");
                Project project = new Project();
                project.addBuildListener(new BuildListener() {
                    @Override
                    public void buildStarted(BuildEvent be) {
                        String s = String.format("Build of %s started", be.getProject().getName());
                        log.info(s);
                        pm.setNote(clipString(s));
                    }

                    @Override
                    public void buildFinished(BuildEvent be) {
                        progressCounter.inc();
                        String s = String.format("Build of %s finished", be.getProject().getName());
                        log.info(s);
                        pm.setNote(clipString(s));
                    }

                    @Override
                    public void targetStarted(BuildEvent be) {
                        String s = String.format("Target %s started: description %s, source %s", be.getTarget().getName(), be.getTarget().getDescription(), be.getSource());
                        pm.setNote(clipString(s));
                    }

                    @Override
                    public void targetFinished(BuildEvent be) {
                        String s = String.format("Finished Target %s : description %s, source %s", be.getTarget().getName(), be.getTarget().getDescription(), be.getSource());
                        log.info(s);
                        try {
                            progressCounter.inc();
                            pm.setNote(clipString(s));
                        } catch (NullPointerException e) {
                            log.warning(String.format("Could not set note on finishing target: %s", e.toString()));
                        }
                    }

                    @Override
                    public void taskStarted(BuildEvent be) {

                        String s = String.format("Task %s started, type %s, target %s, message %s, description %s",
                                be.getTask().getTaskName(),
                                be.getTask().getTaskType(),
                                be.getTarget(),
                                be.getMessage(),
                                be.getTask().getDescription());
                        log.info(s);
                        pm.setNote(clipString(s));
                    }

                    @Override
                    public void taskFinished(BuildEvent be) {
                        progressCounter.inc();
                        String s = String.format("Task %s finished, type %s, source %s", be.getTask().getTaskName(), be.getTask().getTaskType(), be.getSource());
                        log.info(s);
                        pm.setNote(clipString(s));
                    }

                    @Override
                    public void messageLogged(BuildEvent be) {
//                        pm.setNote(be.getMessage());
                        progressCounter.inc();
                        log.fine(be.getMessage());
                    }

                });
                CustomOutputStream errStream = new CustomOutputStream(log, Level.WARNING);
                CustomOutputStream stdStream = new CustomOutputStream(log, Level.FINE);
                try {
                    parent.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    log.log(Level.INFO, "Build file is {0}", buildFile.getAbsolutePath());
                    project.setUserProperty("ant.file", buildFile.getAbsolutePath());

                    project.init();
                    ProjectHelper helper = ProjectHelper.getProjectHelper();

                    project.addReference("ant.projectHelper", helper);
                    helper.parse(project, buildFile);
//                    project.setProperty("javac.deprecation", "false");
//                    project.setUserProperty("javac.deprecation", "false");
                    // http://blog.adeel.io/2017/05/06/redirecting-all-stdout-and-stderr-to-logger-in-java/
                    System.setErr(
                            new PrintStream(
                                    errStream //Or whatever logger level you want
                            )
                    );
                    System.setOut(
                            new PrintStream(
                                    stdStream //Or whatever logger level you
                            )
                    );

                    pm.setNote("Building jAER");
                    log.info(project.getTargets().toString());
//                    if (loggingWindow != null) {
//                        loggingWindow.clear();
//                    }
                    project.executeTarget("jar");
//                    project.executeTarget(project.getDefaultTarget());
                    progressCounter.saveEstimatedTotalWork();
                    pm.close();
                    JOptionPane.showMessageDialog(parent, "<html>Build finished. <p> <b>Restart jAER to see changes.</b>", "Build result", JOptionPane.INFORMATION_MESSAGE);

                } catch (BuildException e) {
                    log.severe(e.toString());
                    pm.close();
                    JOptionPane.showMessageDialog(parent, e.toString(), "Build failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    parent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }).start();
        };

    }

    private static class StringBuilderStream extends OutputStream {

        StringBuilder stringBuilder;

        public StringBuilderStream() {
            stringBuilder = new StringBuilder();
        }

        @Override
        public final void write(int i) throws IOException {
            char c = (char) i;
            if (c == '\r' || c == '\n') {
                if (stringBuilder.length() > 0) {
                    stringBuilder = new StringBuilder();
                }
            } else {
                stringBuilder.append(c);
            }
        }

    }

    // http://blog.adeel.io/2017/05/06/redirecting-all-stdout-and-stderr-to-logger-in-java/
    private static class CustomOutputStream extends OutputStream {

        Logger logger;
        Level level;
        private StringBuilder stringBuilder;

        /**
         * Custom output stream or logging stdout and stderr.
         *
         * @param logger the logger to use to log the stream
         * @param level what level to log this stream
         */
        public CustomOutputStream(Logger logger, Level level) {
            this.logger = logger;
            this.level = level;
            stringBuilder = new StringBuilder();
        }

        /**
         * Writes the character to the stream. On every newline the stream is
         * logged out to the logger defined on construction. // * If string
         * contains "error" the level is elevated to SEVERE to flag compile
         * errors.
         *
         * @param i the character.
         * @throws IOException
         */
        @Override
        public final void write(int i) throws IOException {
            char c = (char) i;
            if (c == '\r' || c == '\n') {
                if (stringBuilder.length() > 0) {
                    logger.log(stringBuilder.toString().contains("error") ? Level.SEVERE : level, stringBuilder.toString());
                    // logging output already goes to the JaerConsoleLoggerWindow
//                    if (level.intValue() >= Level.INFO.intValue()) {
//                        if (loggingWindow == null) {
//                            loggingWindow = new AEViewerConsoleOutputFrame();
//                            loggingWindow.setTitle("jAER Build output");
//                        }
//                        loggingWindow.append(stringBuilder.toString(), level);
//                        loggingWindow.setVisible(true);
//                    }
                    stringBuilder = new StringBuilder();
                }
            } else {
                stringBuilder.append(c);
            }
        }

    }

    /**
     * Wraps text with <html> to 60 character lines with breaks and replaces
     * line breaks with <br>
     *
     * @param s the string to wrap
     * @return the wrapped string
     */
    private static String wrapString(String s) {
        s = "<HTML>" + WordUtils.wrap(s, 60).replaceAll("\n", "<br>");
        return s;
    }

    /**
     * Clips string to 80 characters keeping start and end and replacing
     * newlines by spaces
     *
     * @param s the input string
     * @return the output string
     */
    private static String clipString(String s) {
        if (s.length() <= 80) {
            return s.replaceAll("\n", " ");
        }
        // clip middle part retaining 80 characters
        int n = s.length();
        int keep = 36;
        s = s.replaceAll("\n", " ");
        String s0 = s.substring(0, keep);
        String s1 = s.substring(n - keep);
        return s0 + "..." + s1;
    }

    /**
     * Action listener that runs git fetch/checkout
     *
     * @param parent display progress dialog over this component
     * @return the listener
     * @throws IOException if .git does not exist
     */
    public static ActionListener gitUpdateActionListener(Component parent) throws IOException {
        throwIoExceptionIfNoGit();
        return (ActionEvent ae) -> {
            new Thread(() -> {

                parent.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                final javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Update Task",
                        "Task starting", 0, 100);
                pm.setMillisToDecideToPopup(1);
                pm.setMillisToPopup(1);

                ProgressCounter progressCounter = new ProgressCounter("git-update", pm, 100);

                class MyAppender extends OutputStreamAppender<ILoggingEvent> {

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    javax.swing.ProgressMonitor pm;
                    ProgressCounter pc;

                    public MyAppender(javax.swing.ProgressMonitor pm, ProgressCounter pc) {
                        this.pm = pm;
                        this.pc = pc;
                        setOutputStream(stream);
                    }

                    @Override
                    protected void append(ILoggingEvent eventObject) {
                        String formattedMessage = eventObject.getFormattedMessage();
                        log.info(formattedMessage);
//                        formattedMessage = "<HTML>" + WordUtils.wrap(formattedMessage, 30);

                        pc.inc();
//                        pm.setNote(clipString(formattedMessage));

                    }

                    @Override
                    protected void writeOut(ILoggingEvent event) throws IOException {
                        super.writeOut(event); //To change body of generated methods, choose Tools | Templates.
                    }

                }

                org.slf4j.Logger gitLogger = (org.slf4j.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
                if (gitLogger instanceof ch.qos.logback.classic.Logger) {
//                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                    Handler handler = new StreamHandler(stream, new SimpleFormatter());
                    ch.qos.logback.classic.Logger logBackLogger = (ch.qos.logback.classic.Logger) gitLogger;
                    LoggerContext context = logBackLogger.getLoggerContext();

                    PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
                    logEncoder.setContext(context);
                    logEncoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level - %msg%n");
                    logEncoder.start();

                    MyAppender appender = new MyAppender(pm, progressCounter);
                    appender.setName("gitprogress");
                    appender.setEncoder(logEncoder);
                    appender.setContext(context);
                    appender.start();
                    logBackLogger.setAdditive(false);
//                    logBackLogger.addAppender(appender);
                    StatusPrinter.print(context);

                }
                progressCounter.inc();
                try (Git git = Git.open(new File("."))) {
                    final FetchCommand fetch = git.fetch();
//                    final PullCommand pull = git.pull();
//                    pull.setFastForward(MergeCommand.FastForwardMode.FF);

                    BatchingProgressMonitor antProgMon = new BatchingProgressMonitor() {
                        @Override
                        protected void onUpdate(String taskName, int workCurr) {
//                            pm.setNote(clipString(taskName));
                            pm.setProgress(workCurr);
                            if (pm.isCanceled()) {
                                endTask();
                            }
                        }

                        @Override
                        protected void onEndTask(String taskName, int workCurr) {
//                            pm.setNote(clipString(taskName));
                            pm.setProgress(workCurr);
                        }

                        @Override
                        protected void onUpdate(String taskName, int workCurr, int workTotal, int percentDone) {
//                            pm.setNote(clipString(taskName));
                            pm.setMaximum(workTotal);
                            pm.setProgress(workCurr);
                            if (pm.isCanceled()) {
                                endTask();
                            }
                        }

                        @Override
                        protected void onEndTask(String taskName, int workCurr, int workTotal, int percentDone) {
                            pm.close();
                        }
                    };

                    fetch.setProgressMonitor(antProgMon);
                    antProgMon.beginTask("Beginning fetch", org.eclipse.jgit.lib.ProgressMonitor.UNKNOWN);
                    fetch.setRemote("origin");
//                    fetch.setRemoteBranchName("master");
                    final FetchResult result = fetch.call();
                    antProgMon.endTask();
                    progressCounter.inc();
                    String s = "Git fetch result messages: " + result.getMessages();
                    log.info(s);
                    pm.setNote(s);
                    CheckoutCommand checkoutCommand = git.checkout();
                    checkoutCommand.setProgressMonitor(antProgMon);
                    antProgMon.beginTask("Beginning checkout", org.eclipse.jgit.lib.ProgressMonitor.UNKNOWN);
                    checkoutCommand.setStartPoint("master");
                    checkoutCommand.setCreateBranch(true);
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
                    LocalDateTime now = LocalDateTime.now();
                    String branchName = "update-" + dtf.format(now);
                    String s2 = "checking out changes from master to new branch named " + branchName;
                    log.info(s2);
                    pm.setNote(s2);
                    checkoutCommand.setName(branchName);
                    progressCounter.inc();
                    checkoutCommand.call();
                    antProgMon.endTask();
                    progressCounter.inc();
                    final CheckoutResult checkoutResult = checkoutCommand.getResult();
                    log.info("Git checkout succeeded");
                    pm.close();
                    progressCounter.saveEstimatedTotalWork();
                    String msg = "<html> Git checkout to " + branchName + " succeeded. <p> <b> Remember to switch back to <i>master</i> branch if debugging update!</b>";
                    JOptionPane.showMessageDialog(parent, msg, "Git checkout succeeded", JOptionPane.INFORMATION_MESSAGE);
//                    git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit
// not needed for fetch/checkout
                } catch (Exception e) {
                    log.warning(e.toString());
                    pm.close();
                    JOptionPane.showMessageDialog(parent, e.toString(), "Git update failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    parent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }).start();
        };
    }

    public static ActionListener gitStatusActionListener(Component parent) throws IOException {
        throwIoExceptionIfNoGit();
        return (ae) -> {
            new Thread(() -> {
                try (Git git = Git.open(new File("."))) {
                    parent.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    FetchCommand fetch = git.fetch();
                    fetch.setRemote("origin");
                    FetchResult result = fetch.call();
                    log.info("Git fetch result messages: " + result.getMessages());
                    String latestTag = getLatestTag(git);
                    String buildVersion = JaerConstants.getBuildVersion();
                    log.info(String.format("latest tag after fetch is %s", latestTag));
                    String s = String.format("<html>Lastest remote release tag: %s \n\nYour build version info: \n%s", latestTag, buildVersion);
                    JOptionPane.showMessageDialog(parent, s, "Latest tag", JOptionPane.INFORMATION_MESSAGE);
//                    git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Check failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    parent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }).start();
        };
    }

    /**
     * Returns latest tag (i.e. release, e.g. 1.9.0)
     *
     * @param git
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    private static String getLatestTag(Git git) throws GitAPIException, IOException {

        DescribeCommand describe = git.describe();
        describe.setTags(true); // must use this to get annotations that correspond to release tags, otherwise we only get old tags for some reason
        String latestTagName = describe.call();
        log.info("latest tag is named {}".format(latestTagName));
        writeVersionToVerFile(latestTagName);
        return latestTagName;
    }

    public static ActionListener initReleaseForGitActionListener(JaerUpdaterFrame parent) throws IOException {
        UIManager.put("ProgressMonitor.progressText", "Git Release Initialization Progress");
        return (ActionEvent ae) -> {
            new Thread(() -> {
                if (!DEBUG) {
                    try {
                        throwIoExceptionIfNoGit();
                        log.warning("git exists, will not try to initialize it");
                        JOptionPane.showMessageDialog(parent, ".git folder already exists and can be opened, will not do anything", "Git already exists", JOptionPane.ERROR_MESSAGE);
                        parent.setGitButtonsEnabled(true);
                        parent.setCursor(null);
                        return;
                    } catch (IOException ex) {
                        log.info("git not found, proceeeding");
                    }
                }
                long freebytes = new File(".").getFreeSpace();
                float gb = (float) freebytes / (1 << 30);
                if (gb < 2) {
                    int ret = JOptionPane.showConfirmDialog(parent,
                            String.format("<html>There is only %.1fGB of free disk space. Cloning will require at least 1GB free space."
                                    + "<p><p>Do you want to proceed?", (gb)),
                            "Confirm git initialization operation",
                            JOptionPane.YES_NO_OPTION);
                    if (ret != JOptionPane.YES_OPTION) {
                        JOptionPane.showMessageDialog(parent, "Git initialization operation cancelled");
                        return;
                    }
                }
                // confirm operation
                int ret = JOptionPane.showConfirmDialog(parent,
                        "<html>This operation will clone jaer to a new temporary folder"
                        + "<br>and then copy the clone to your jAER working folder.</p>"
                        + "<p>It can potentially overwrite files that you may have modified,"
                        + "<br>like bias or filter settings."
                        + "<p>You will be asked about overwriting files that have been modified<br>"
                        + "after you installed the release.<p>"
                        + "<p> <b>Note:</b> If you installed jAER to a system folder, you might need to run jAER with adminstrator/root privaleges"
                        + "<p>Do you want to proceed?</p>",
                        "<p> Confirm git initialization operation</p>",
                        JOptionPane.YES_NO_OPTION);
                if (ret != JOptionPane.YES_OPTION) {
                    JOptionPane.showMessageDialog(parent, "Git initialization operation cancelled");
                    return;
                }
                final Path source = Paths.get("jaer-clone");
                // debug, copy to new folder, not here at root level
//                final Path target = Paths.get("jaer-clone-copy");   // we will create .git and also copy all non-existing files to starting folder
                final Path target = DEBUG ? Paths.get("jaer-clone-copy") : Paths.get(".");   // we will create .git and also copy all non-existing files to starting folder
                log.info(JaerConstants.JAER_HOME + " cloning to folder " + source.toString());
                parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                CloneCommand cloneCmd = Git.cloneRepository();
                cloneCmd.setURI(JaerConstants.JAER_HOME);
                cloneCmd.setDirectory(source.toFile());
                GitProgressMonitor gpm = new GitProgressMonitor(parent, "cloning " + JaerConstants.JAER_HOME);
                cloneCmd.setProgressMonitor(gpm);
                try (Git git = cloneCmd.call()) {
                    log.info("cloned " + git.toString());
                    git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit
                } catch (Exception e) {
                    log.warning(e.toString());
                    if (!DEBUG) {
                        JOptionPane.showMessageDialog(parent, e.toString(), "Cloning failed", JOptionPane.ERROR_MESSAGE);
                    }

                    if (!DEBUG) {
                        parent.setCursor(null);
                        return;
                    }
                } finally{
                    gpm.close();
                }
                if (gpm.isCancelled()) {
                    parent.setCursor(null);
                    return;
                }

                // copy new files/folder to targets
                log.info("copying non-existing files to release");
                // https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileVisitor.html
                int estimatedCloneTotalFiles = prefs.getInt("JaerUpdaterFrame.estimatedCloneTotalFiles", 10000);
                javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "moving clone", source.toString(), 0, estimatedCloneTotalFiles);
                Mover mover = new Mover(source, target, pm, parent);
                SwingWorker fileMover = new SwingWorker() {
                    @Override
                    protected Object doInBackground() throws Exception {
                        try {
                            Files.walkFileTree(source, mover);
                            prefs.putInt("JaerUpdaterFrame.estimatedCloneTotalFiles", mover.fileCount);
                            StringBuilder sb = new StringBuilder("file errors\n");
                            sb.append("Could not copy following files");
                            for (Path p : mover.filesCouldNotCopy) {
                                sb.append(p.toString() + "\n");
                            }
                            sb.append("\nCould not delete following files\n");
                            for (Path p : mover.filesCouldNotDelete) {
                                sb.append(p.toString() + "\n");
                            }
                            log.info(sb.toString());
                            final String msg = String.format("Moved %d files from %s to %s, skipped %d files", mover.filesAdded.size(), source, target, mover.filesSkipped.size());
                            log.info(msg);
                            JOptionPane.showMessageDialog(parent, msg, "Copying git clone done", JOptionPane.INFORMATION_MESSAGE);
                            parent.setGitButtonsEnabled(true);
                        } catch (HeadlessException | IOException e) {
                            log.warning(e.toString());
                            JOptionPane.showMessageDialog(parent, e.toString(), "Copying git clone failed", JOptionPane.ERROR_MESSAGE);
                        } finally{
                            pm.close();
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        pm.close();
                    }
                };
                fileMover.addPropertyChangeListener(mover);
                fileMover.execute();
                parent.setCursor(null);

            }).start();
        };
    }

    static private class Mover extends SimpleFileVisitor<Path> implements PropertyChangeListener {

        Path source, target;
        int folderCount = 0, fileCount = 0;
        ArrayList<Path> filesAdded = new ArrayList(1000), filesSkipped = new ArrayList(500), filesCouldNotDelete = new ArrayList(100), filesCouldNotCopy = new ArrayList(100);
        javax.swing.ProgressMonitor pm;
        Component parent;

        Mover(Path source, Path target, javax.swing.ProgressMonitor pm, Component parent) {
            this.source = source;
            this.target = target;
            this.pm = pm;
            this.parent = parent;
            pm.setMillisToDecideToPopup(100);
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException ioe) throws IOException {
            try {
                Files.delete(dir);
            } catch (IOException e) {
                log.warning("could not delete folder " + dir + ": caught " + e.toString());
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path t, IOException ioe) throws IOException {
            log.warning(String.format("failed to copy %s: caught %s", t.toAbsolutePath(), ioe.toString()));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            if (pm.isCanceled()) {
                pm.close();
                return FileVisitResult.TERMINATE;
            }
            folderCount++;
            Path targetdir = target.resolve(source.relativize(dir));
            if (Files.exists(targetdir, LinkOption.NOFOLLOW_LINKS)) {
                pm.setNote("skipping existing folder " + dir);
            } else {
                try {
                    pm.setNote("making folder " + targetdir);
                    Files.copy(dir, targetdir);
                } catch (FileAlreadyExistsException e) {
                    log.warning("got " + e.toString());
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
            if (pm.isCanceled()) {
                pm.close();
                return FileVisitResult.TERMINATE;
            }
            fileCount++;
            final Path targetFile = target.resolve(source.relativize(file));
            if (Files.exists(targetFile, LinkOption.NOFOLLOW_LINKS)) {
                // File exists locally already, but it might be an older version than in current HEAD, or it might have
                // been locally modified, e.g. a bias or filter settings file or modified shell script.  
                // (The file date of new clone will always be later, so it is not useful.)
                // We assume that the current HEAD version is authoritative except for xml and shell scripts.
                // we ask for overwriting any file that has been modified more than 1 hour after the modification time of the 
                // windows launcher file, which was set when the release was prepared.
                FileTime rootTime = Files.getLastModifiedTime(Paths.get("jAERViewer_win64.exe"), LinkOption.NOFOLLOW_LINKS);
                FileTime srcTime = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
                FileTime targetTime = Files.getLastModifiedTime(targetFile, LinkOption.NOFOLLOW_LINKS);
                String s = FilenameUtils.getExtension(targetFile.getFileName().toString()).toLowerCase();
                Instant targetTimeInstant = targetTime.toInstant();
                Instant nowInstant = Instant.now();
                Instant rootInstant = rootTime.toInstant();
                long hoursAfterRootModified = rootInstant.until(targetTimeInstant, ChronoUnit.HOURS);
                long daysAgoModified = targetTimeInstant.until(nowInstant, ChronoUnit.DAYS);
//                if ("xml".equals(s) || "sh".equals(s) || "bash".equals(s) | s.isEmpty()) {
                if (hoursAfterRootModified > 1) {
                    String q = String.format("<html>%s <br>exists. <p> It was modified on %s, %d hours after the root of project (%d days ago). <p>Overwrite it?",
                            targetFile.toString(), targetTime.toString(), hoursAfterRootModified, daysAgoModified);
                    int ret = JOptionPane.showConfirmDialog(parent, q, "Overwrite local file?", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (ret != JOptionPane.YES_OPTION) {
                        log.info(String.format("source file %s dated %s"
                                + ", target file %s dated %s, skipping it",
                                file, srcTime.toString(), targetFile, targetTime.toString()));
                        filesSkipped.add(file);
                        pm.setProgress(fileCount);
                        if (ret == JOptionPane.NO_OPTION) {
                            return FileVisitResult.CONTINUE;
                        } else if (ret == JOptionPane.CANCEL_OPTION) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                }
                try {
                    Files.delete(targetFile);
                } catch (IOException e) {
                    log.warning("could not delete existing target file " + targetFile + ": caught " + e.toString());
                    filesCouldNotDelete.add(targetFile);
                    return FileVisitResult.CONTINUE;
                }
            }
            try {
                filesAdded.add(file);
                Files.copy(file, targetFile);
                pm.setProgress(fileCount);
            } catch (IOException ex) {
                log.warning("error copying " + file + ": caught " + ex.toString());
                filesCouldNotCopy.add(file);
            }
            try {
                Files.delete(file);
            } catch (IOException e) {
                log.warning("could not delete source file " + file);
                filesCouldNotDelete.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("progress" == evt.getPropertyName()) {
                int progress = (Integer) evt.getNewValue();
                pm.setProgress(progress);
                String message
                        = String.format("Completed %d%%.\n", progress);
                pm.setNote(message);
            }
        }
    }

    /**
     * Writes to global version file for About dialog and other purposes
     *
     * @param version
     */
    private static void writeVersionToVerFile(String version) throws IOException {
        FileWriter writer = new FileWriter(JaerConstants.VERSION_FILE);
        writer.write(version);
        writer.close();
    }

    private static void restart() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static class ProgressCounter {

        String name = "";
        ProgressMonitor pm;
        int estimatedTotalWork = 10000;

        public ProgressCounter(String name, ProgressMonitor pm, int estimatedTotalWork) {
            this.name = name;
            this.pm = pm;
            this.estimatedTotalWork = prefs.getInt("estimatedTotalWork." + name, estimatedTotalWork);
            pm.setMaximum(estimatedTotalWork);
        }

        int progress = 0;

        int inc() {
            if (++progress > pm.getMaximum()) {
                pm.setMaximum(progress * 2);
            }
            pm.setProgress(progress);
            return ++progress;
        }

        int getProgress() {
            return progress;
        }

        int getEstimatedTotalWork() {
            return estimatedTotalWork;
        }

        void setEstimatedTotalWork(int w) {
            this.estimatedTotalWork = w;
            pm.setMaximum(w);
        }

        void saveEstimatedTotalWork() {
            prefs.putInt("estimatedTotalWork." + name, progress);
        }

        @Override
        public String toString() {
            return "ProgressCounter{" + "name=" + name + ", progress=" + progress + ", estimatedTotal=" + getEstimatedTotalWork() + '}';
        }

    }

    private static class GitProgressMonitor implements org.eclipse.jgit.lib.ProgressMonitor {

        ProgressCounter pc;
        javax.swing.ProgressMonitor pm;
        Component parent;
        String name;
        String taskTitle = null;

        public GitProgressMonitor(Component parent, String name) {
            this.parent = parent;
            this.name = name;
            this.pm = new javax.swing.ProgressMonitor(this.parent, this.name,
                    "Starting " + name, 0, 100000);
            this.pc = new ProgressCounter(name,this.pm,100000);
            pm.setMillisToDecideToPopup(100);
            pm.setMillisToPopup(100);
        }

        @Override
        public void start(int totalTasks) {
            pm.setNote("Starting work on " + totalTasks + " tasks");
            pm.setMaximum(totalTasks);
        }

        @Override
        public void beginTask(String title, int totalWork) {
            this.taskTitle = title;
            pm.setNote("Starting task " + title + " with " + totalWork + " tasks");
            pm.setMaximum(totalWork);
        }

        @Override
        public void update(int completed) {
            pc.inc();
            pm.setNote("completed " + pc.progress + " / " + pm.getMaximum() + " on " + taskTitle);
        }

        @Override
        public void endTask() {
            pm.setNote("Done");
            pm.close();
        }

        @Override
        public boolean isCancelled() {
            return pm.isCanceled();

        }

        private void close() {
            pm.close();
        }
    }

}
