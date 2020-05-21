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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.util.StatusPrinter;
import com.google.common.base.Splitter;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import org.apache.commons.text.WordUtils;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.slf4j.LoggerFactory;
import org.slf4j.event.LoggingEvent;

/**
 * Handles self update git version/tag check, git pull, and ant rebuild, via
 * JGit and ant.
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdater {

    private static Logger log = Logger.getLogger("JaerUpdater");
    private static Preferences prefs = Preferences.userNodeForPackage(JaerUpdater.class);

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
                        progressCounter.saveProgress();
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
                        progressCounter.saveProgress();
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
                    JOptionPane.showMessageDialog(parent, "<html>Build error: <p> "+e.toString(), "Buld failed", JOptionPane.ERROR_MESSAGE);
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
     */
    public static ActionListener gitPullActionListener(Component parent) {
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
                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Pull failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };
    }

    public static ActionListener gitCFetchChangesActionListener(Component parent) {
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

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Check failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };
    }

    private static String getLatestTag(Git git) throws GitAPIException, IOException {
// open a cloned repository
//        FileRepositoryBuilder builder = new FileRepositoryBuilder();
//        Repository repository = builder.setGitDir(new File(".git"))
//                .readEnvironment()
//                .findGitDir()
//                .build();
//
//        final RevWalk walk = new RevWalk(repository);
//        List<Ref> refList = new Git(repository).tagList().call();

//        List<Ref> refList = git.tagList().call();
//        final RevWalk walk = new RevWalk(git.getRepository());
//        RevTag rt;
//        Collections.sort(refList, new Comparator<Ref>() {
//            public int compare(Ref o1, Ref o2) {
//                java.util.Date d1 = null;
//                java.util.Date d2 = null;
//                try {
//                    d1 = walk.parseTag(o1.getObjectId()).getTaggerIdent().getWhen();
//                    d2 = walk.parseTag(o2.getObjectId()).getTaggerIdent().getWhen();
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                return d1.compareTo(d2);
//            }
//        });

//        List<Ref> list = git.tagList().call();
//        ObjectId commitId = ObjectId.fromString("hash");
//        Collection<ObjectId> commits = new LinkedList<ObjectId>();
//        for (Ref tag : list) {
//            ObjectId object = tag.getObjectId();
//            if (object.equals(commitId)) {;
//                commits.add(object);
//            }
//        }
        DescribeCommand cmd = git.describe();
//        cmd.setTarget("HEAD");
        cmd.setTarget("origin/master");
        String version = cmd.call();
        log.info(String.format("latest tag found from git repo: %s", version));
        writeVersionToVerFile(version);
        return version;
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
            prefs.putInt("buildTotalProgressCount." + name, progress);
        }
    }

}
