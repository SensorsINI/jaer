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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.UIManager;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;
import scala.collection.immutable.ListSet;

/**
 * Handles self update and rebuild, via git and ant
 *
 * @author Tobi Delbruck (tobi@ini.uzh.ch)
 *
 */
public class JaerUpdater {

    public static final String JAER_HOME = "https://github.com/SensorsINI/jaer.git";
    private static Logger log = Logger.getLogger("JaerUpdater");

    public static ActionListener buildActionListener(Component parent) {
        UIManager.put("ProgressMonitor.progressText", "Build Progress");

        return (ActionEvent ae) -> {
            new Thread(() -> {
                //creating ProgressMonitor instance
                class ProgressCounter {

                    int progress = 0;

                    int inc() {
                        return ++progress;
                    }

                    int inc(int amt) {
                        return progress += amt;
                    }
                }
                final ProgressCounter progressCounter = new ProgressCounter();
                javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Build Task",
                        "Task starting", 0, 60000); // adjust for approximate build time

                //decide after 100 millis whether to show popup or not
                pm.setMillisToDecideToPopup(100);
                //after deciding if predicted time is longer than 100 show popup
                pm.setMillisToPopup(100);
                File buildFile = new File("build.xml");
                Project p = new Project();
                p.addBuildListener(new BuildListener() {
                    @Override
                    public void buildStarted(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc());
                    }

                    @Override
                    public void buildFinished(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.close();
                    }

                    @Override
                    public void targetStarted(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc(100));
                    }

                    @Override
                    public void targetFinished(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc(100));
                    }

                    @Override
                    public void taskStarted(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc(10));
                    }

                    @Override
                    public void taskFinished(BuildEvent be) {
                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc(10));
                    }

                    @Override
                    public void messageLogged(BuildEvent be) {
//                        pm.setNote(be.getMessage());
                        pm.setProgress(progressCounter.inc());
                    }
                });
                p.setUserProperty("ant.file", buildFile.getAbsolutePath());

                log.info("Build file is " + buildFile.getAbsolutePath());
                p.init();
                ProjectHelper helper = ProjectHelper.getProjectHelper();

                p.addReference("ant.projectHelper", helper);
                helper.parse(p, buildFile);
                pm.setNote("building jAER");
                p.executeTarget(p.getDefaultTarget());

                pm.setNote("Build finished");
                pm.close();
                JOptionPane.showMessageDialog(parent, "Build finished", "Buld result", JOptionPane.INFORMATION_MESSAGE);

            }).start();
        };

    }

    public static ActionListener gitPullActionListener(Component parent) {
        return (ActionEvent ae) -> {
            new Thread(() -> {
                try (Git git = Git.open(new File("."))) {

                    final PullCommand pull = git.pull();
                    
                    final javax.swing.ProgressMonitor pm = new javax.swing.ProgressMonitor(parent, "Update Task",
                            "Task starting", 0, 100); // adjust for approximate build time

                    //decide after 100 millis whether to show popup or not
                    pm.setMillisToDecideToPopup(100);
                    //after deciding if predicted time is longer than 100 show popup
                    pm.setMillisToPopup(100);
                    BatchingProgressMonitor antProgMon = new BatchingProgressMonitor() {
                        @Override
                        protected void onUpdate(String taskName, int workCurr) {
                            pm.setNote(taskName);
                            pm.setProgress(workCurr);
                               if(pm.isCanceled()){
                                endTask();
                            }
                        }

                        @Override
                        protected void onEndTask(String taskName, int workCurr) {
                            pm.setNote(taskName);
                            pm.setProgress(workCurr);
                        }

                        @Override
                        protected void onUpdate(String taskName, int workCurr, int workTotal, int percentDone) {
                            pm.setNote(taskName);
                            pm.setMaximum(workTotal);
                            pm.setProgress(workCurr);
                            if(pm.isCanceled()){
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
                    JOptionPane.showMessageDialog(parent, result.toString(), "Pull result", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Pull failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };
    }

        public static ActionListener gitCheckUpdatesActionListener(Component parent) {
        return (ae) -> {
            new Thread(() -> {
                try (Git git = Git.open(new File("."))) {
                    FetchCommand fetch = git.fetch();
                    fetch.setRemote("origin");
                    FetchResult result = fetch.call();
                    log.info("Git fetch result: " + result.toString());
                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(parent, e.toString(), "Check failed", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        };
    }
}
