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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
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
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.apache.commons.io.FileUtils;
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
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;

/**
 * Handles self update git version/tag check, git pull, and ant rebuild, via
 * JGit and ant.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdater {

    public static final boolean DEBUG = false; // false for production version,  true to clone here to tmp folders that do not overwrite our own .git
    private static Logger log = Logger.getLogger("JaerUpdater");
    private static Preferences prefs = Preferences.userNodeForPackage(JaerUpdater.class);

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
            log.warning("could not open Git repository " + f.getAbsolutePath()+": caught "+e.toString());
            throw new IOException(e.toString());
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
                //creating ProgressMonitor instance
                final ProgressCounter progressCounter = new ProgressCounter("build");
                javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Build Task",
                        "Build starting", 0, progressCounter.getEstimatedTotal()); // adjust for approximate build time

                //decide after 100 millis whether to show popup or not
                pm.setMillisToDecideToPopup(100);
                //after deciding if predicted time is longer than 100 show popup
                pm.setMillisToPopup(100);
                File buildFile = new File("build.xml");
                Project p = new Project();
                p.addBuildListener(new BuildListener() {
                    @Override
                    public void buildStarted(BuildEvent be) {
                        String s = String.format("Build of %s started", be.getProject().getName());
                        pm.setNote(wrap(s));
                        pm.setProgress(progressCounter.inc());
                    }

                    @Override
                    public void buildFinished(BuildEvent be) {
                        String s = String.format("Build of %s finished", be.getProject().getName());
                        pm.setNote(wrap(s));
                        progressCounter.saveProgress();
                        pm.close();
                    }

                    @Override
                    public void targetStarted(BuildEvent be) {
                        String s = String.format("Target %s started: description ", be.getTarget().getName(), be.getTarget().getDescription());
                        pm.setNote(wrap(s));
                        pm.setProgress(progressCounter.inc(100));
                    }

                    @Override
                    public void targetFinished(BuildEvent be) {
                        String s = String.format("Target %s finished: description ", be.getTarget().getName(), be.getTarget().getDescription());
                        pm.setNote(wrap(s));
                        pm.setProgress(progressCounter.inc(100));
                    }

                    @Override
                    public void taskStarted(BuildEvent be) {
                        String s = String.format("Task %s started, type %s", be.getTask().getTaskName(), be.getTask().getTaskType());
                        pm.setNote(wrap(s));
                        pm.setProgress(progressCounter.inc(10));
                    }

                    @Override
                    public void taskFinished(BuildEvent be) {
                        String s = String.format("Task %s finished, type %s", be.getTask().getTaskName(), be.getTask().getTaskType());
                        pm.setNote(wrap(s));
                        pm.setProgress(progressCounter.inc(10));
                    }

                    @Override
                    public void messageLogged(BuildEvent be) {
//                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc());
                    }
                });
                try {
                    log.info("Build file is " + buildFile.getAbsolutePath());
                    p.setUserProperty("ant.file", buildFile.getAbsolutePath());

                    p.init();
                    ProjectHelper helper = ProjectHelper.getProjectHelper();

                    p.addReference("ant.projectHelper", helper);
                    helper.parse(p, buildFile);
                    pm.setNote("Building jAER");
                    p.executeTarget(p.getDefaultTarget());

                    pm.setNote("Build finished");
                    pm.close();
                    JOptionPane.showMessageDialog(parent, "<html>Build finished. <p> <b>Restart jAER to see changes.</b>", "Buld result", JOptionPane.INFORMATION_MESSAGE);
                } catch (BuildException e) {
                    JOptionPane.showMessageDialog(parent, "<html>Build error: <p> " + e.toString(), "Buld failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };

    }

    private static String wrap(String s) {
        s = "<HTML>" + WordUtils.wrap(s, 25);
        return s;
    }

    /**
     * Action listener that runs git pull
     *
     * @param parent display progress dialog over this component
     * @return the listener
     * @throws IOException if .git does not exist
     */
    public static ActionListener gitPullActionListener(Component parent) throws IOException {
        throwIoExceptionIfNoGit();
        return (ActionEvent ae) -> {
            new Thread(() -> {

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
                        formattedMessage = "<HTML>" + WordUtils.wrap(formattedMessage, 30);

                        pm.setNote(wrap(formattedMessage));
                        pc.inc();
                        pm.setProgress(pc.getProgress());
                        pm.setMaximum(pc.getEstimatedTotal());

                    }

                    @Override
                    protected void writeOut(ILoggingEvent event) throws IOException {
                        super.writeOut(event); //To change body of generated methods, choose Tools | Templates.
                    }

                }

                org.slf4j.Logger gitLogger = (org.slf4j.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
                final ProgressCounter progressCounter = new ProgressCounter("git.pull");
                final javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Update Task",
                        "Task starting", 0, 100); // adjust for approximate build time
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
                    logBackLogger.addAppender(appender);
                    StatusPrinter.print(context);

                }
                try (Git git = Git.open(new File("."))) {

                    final PullCommand pull = git.pull();

                    //decide after 100 millis whether to show popup or not
                    pm.setMillisToDecideToPopup(1);
                    //after deciding if predicted time is longer than 100 show popup
                    pm.setMillisToPopup(1);
                    BatchingProgressMonitor antProgMon = new BatchingProgressMonitor() {
                        @Override
                        protected void onUpdate(String taskName, int workCurr) {
                            pm.setNote(wrap(taskName));
                            pm.setProgress(workCurr);
                            if (pm.isCanceled()) {
                                endTask();
                            }
                        }

                        @Override
                        protected void onEndTask(String taskName, int workCurr) {
                            pm.setNote(wrap(taskName));
                            pm.setProgress(workCurr);
                        }

                        @Override
                        protected void onUpdate(String taskName, int workCurr, int workTotal, int percentDone) {
                            pm.setNote(wrap(taskName));
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

                    pull.setProgressMonitor(antProgMon);
                    antProgMon.beginTask("Beginning pull", org.eclipse.jgit.lib.ProgressMonitor.UNKNOWN);
                    pull.setRemote("origin");
                    pull.setRemoteBranchName("master");
                    final PullResult result = pull.call();
                    antProgMon.endTask();
                    log.info("Git pull result: " + result.toString());
                    String s = WordUtils.wrap(result.toString(), 40);
                    JOptionPane.showMessageDialog(parent, s.toString(), "Pull result", JOptionPane.INFORMATION_MESSAGE);
                    git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit
                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Pull failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };
    }

    public static ActionListener gitCFetchChangesActionListener(Component parent) throws IOException {
        throwIoExceptionIfNoGit();
        return (ae) -> {
            new Thread(() -> {
                try (Git git = Git.open(new File("."))) {
                    FetchCommand fetch = git.fetch();
                    fetch.setRemote("origin");
                    FetchResult result = fetch.call();
                    log.info("Git fetch result: " + result.toString());
                    String latestTag = getLatestTag(git);
                    String buildVersion = JaerConstants.getBuildVersion();
                    log.info(String.format("latest tag after fetch is %s", latestTag));
                    String s = String.format("<html>Lastest remote release tag: %s \n\nYour build version info: \n%s", latestTag, buildVersion);
                    JOptionPane.showMessageDialog(parent, s, "Latest tag", JOptionPane.INFORMATION_MESSAGE);
                    git.getRepository().close(); // https://stackoverflow.com/questions/31764311/how-do-i-release-file-system-locks-after-cloning-repo-via-jgit

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Check failed", JOptionPane.ERROR_MESSAGE);
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
                            String.format( "<html>There is only %.1fGB of free disk space. Cloning will require at least 1GB free space."
                            + "<p><p>Do you want to proceed?",(gb)),
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
                        + "<br>and then copy the clone to your jAER working folder."
                        + "<p>It can potentially overwrite files that you may have modified,"
                        + "<br>like bias or filter settings."
                        + "<p>You will be asked about overwriting files that have been modified<br>"
                        + "after you installed release."
                        + "<p><p>Do you want to proceed?",
                        "Confirm git initialization operation",
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
                            final String msg = String.format("moved %d files from %s to %s, skipped %d files", mover.filesAdded.size(), source, target, mover.filesSkipped.size());
                            log.info(msg);
                            JOptionPane.showMessageDialog(parent, msg, "Copying git clone done", JOptionPane.INFORMATION_MESSAGE);
                            pm.close();
                        } catch (Exception e) {
                            log.warning(e.toString());
                            JOptionPane.showMessageDialog(parent, e.toString(), "Copying git clone failed", JOptionPane.ERROR_MESSAGE);

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
                parent.setGitButtonsEnabled(true);

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

        public ProgressCounter(String name) {
            this.name = name;
        }

        int progress = 0;

        int inc() {
            return ++progress;
        }

        int inc(int amt) {
            return progress += amt;
        }

        int getProgress() {
            return progress;
        }

        int getEstimatedTotal() {
            return prefs.getInt("buildTotalProgressCount." + name, 80000);
        }

        void saveProgress() {
            prefs.putInt("totalProgressCount." + name, progress);
        }

        @Override
        public String toString() {
            return "ProgressCounter{" + "name=" + name + ", progress=" + progress + ", estimatedTotal=" + getEstimatedTotal() + '}';
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
            this.pc = new ProgressCounter(name);
            this.pm = new javax.swing.ProgressMonitor(parent, name,
                    "Constructed " + name, 0, pc.getEstimatedTotal());
        }

        @Override
        public void start(int totalTasks) {
            this.pc = new ProgressCounter(name);
            this.pm = new javax.swing.ProgressMonitor(parent, name,
                    "Starting " + name, 0, pc.getEstimatedTotal());
            pm.setMillisToDecideToPopup(100);
            pm.setMillisToPopup(100);
            pm.setNote("Starting work on " + totalTasks + " tasks");
            pm.setMaximum(totalTasks);
        }

        @Override
        public void beginTask(String title, int totalWork) {
            this.pm = new javax.swing.ProgressMonitor(parent, name + ": " + title,
                    "Starting " + title, 0, pc.getEstimatedTotal());
            this.taskTitle = title;
            this.pc = new ProgressCounter(taskTitle);
            pm.setMillisToDecideToPopup(100);
            pm.setNote("Starting task " + title + " with " + totalWork + " tasks");
            pm.setMaximum(totalWork);
        }

        @Override
        public void update(int completed) {
            pm.setProgress(pc.inc(completed));
            pm.setNote("completed " + pc.progress + " / " + pm.getMaximum() + " on " + taskTitle);
        }

        @Override
        public void endTask() {
            pc.saveProgress();
            pm.setNote("Done");
            pm.close();
        }

        @Override
        public boolean isCancelled() {
            return pm.isCanceled();

        }
    }

}
