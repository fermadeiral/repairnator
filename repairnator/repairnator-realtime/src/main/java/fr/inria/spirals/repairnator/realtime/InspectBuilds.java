package fr.inria.spirals.repairnator.realtime;

import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.StateType;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.realtime.serializer.WatchedBuildSerializer;
import fr.inria.spirals.repairnator.states.BearsMode;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * This class is used to refresh regularly the build information.
 * It should be launched in a dedicated thread.
 */
public class InspectBuilds implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InspectBuilds.class);

    public static final int BUILD_SLEEP_TIME = 10;
    public static final int LIMIT_SUBMITTED_BUILDS = 100;
    private static final int NB_ELEMENT_TRAVIS_JOB = 250; // the number of elements returned by Travis Job endpoint

    // this fifo queue contains all the ids of the builds that we observed
    // it prevents us for watching twice the same build
    private CircularFifoQueue<Long> observedBuilds = new CircularFifoQueue<>(NB_ELEMENT_TRAVIS_JOB);

    // we use a ConcurrentLinkedDeque because new builds might be submitted while we iterate over it
    private Deque<Build> waitingBuilds = new ConcurrentLinkedDeque<>();

    private int nbSubmittedBuilds;
    private RTScanner rtScanner;
    private int sleepTime;
    private int maxSubmittedBuilds;
    private WatchedBuildSerializer watchedBuildSerializer;
    private boolean shouldStop;

    public InspectBuilds(RTScanner rtScanner) {
        this.rtScanner = rtScanner;
        this.sleepTime = RepairnatorConfig.getInstance().getBuildSleepTime();
        this.maxSubmittedBuilds = RepairnatorConfig.getInstance().getMaxInspectedBuilds();
        this.watchedBuildSerializer = new WatchedBuildSerializer(this.rtScanner.getEngines(), this.rtScanner);
    }

    /**
     * This is used to stop the thread.
     */
    public void switchOff() {
        this.shouldStop = true;
    }

    /**
     * @return true if the number of build to inspect reach the limit
     */
    public boolean maxSubmittedBuildsReached() {
        return (this.nbSubmittedBuilds >= this.maxSubmittedBuilds);
    }

    public void submitNewBuild(Build build) {
        if (this.maxSubmittedBuilds == -1) {
            throw new RuntimeException("You must set maxSubmittedBuilds before running this.");
        }
        if (this.nbSubmittedBuilds < this.maxSubmittedBuilds) {
            // we do not reached the maximum yet

            // we check if we already inspected this build
            if (!this.observedBuilds.contains(build.getId())) {

                // it's not the case: we add the build to the lists
                this.observedBuilds.add(build.getId());
                this.waitingBuilds.add(build);

                // must be synchronized to avoid concurrent access
                synchronized (this) {
                    this.nbSubmittedBuilds++;
                }
                LOGGER.info("New build submitted (id: "+build.getId()+") Total: "+this.nbSubmittedBuilds+" | Limit: "+maxSubmittedBuilds+")");
            }
        } else {
            LOGGER.debug("Build submission ignored. (maximum reached)");
        }
    }

    @Override
    public void run() {
        LOGGER.debug("Start running inspect builds....");
        if (this.sleepTime == -1) {
            throw new RuntimeException("You must set sleepTime before running this.");
        }
        while (!this.shouldStop) {
            LOGGER.info("Refresh all inspected build status (nb builds: "+this.nbSubmittedBuilds+")");

            // we iterate over all builds to refresh them
            for (Build build : this.waitingBuilds) {
                boolean refreshStatus = RepairnatorConfig.getInstance().getJTravis().refresh(build);
                if (!refreshStatus) {
                    LOGGER.error("Error while refreshing build: "+build.getId());
                } else {

                    // when the refresh worked well, we check if it finished or not

                    if (build.getFinishedAt() != null) {
                        LOGGER.debug("Build finished (id:"+build.getId()+" | Status: "+build.getState()+")");

                        if (build.getState() == StateType.PASSED) {

                            Optional<Build> optionalBeforeBuild = RepairnatorConfig.getInstance().getJTravis().build().getBefore(build, true);
                            if (optionalBeforeBuild.isPresent()) {
                                Build previousBuild = optionalBeforeBuild.get();
                                LOGGER.debug("Previous build: " + previousBuild.getId());

                                BuildToBeInspected buildToBeInspected = null;

                                if (previousBuild.getState() == StateType.FAILED && thereIsDiffOnJavaFile(build, previousBuild)) {
                                    LOGGER.debug("The pair "+previousBuild.getId()+" ["+previousBuild.getState()+"], "+build.getId()+" ["+build.getState()+"] is interesting to be inspected.");
                                    buildToBeInspected = new BuildToBeInspected(previousBuild, build, ScannedBuildStatus.FAILING_AND_PASSING, RepairnatorConfig.getInstance().getRunId());
                                } else {
                                    if (previousBuild.getState() == StateType.PASSED && thereIsDiffOnJavaFile(build, previousBuild) && thereIsDiffOnTests(build, previousBuild)) {
                                        LOGGER.debug("The pair "+previousBuild.getId()+" ["+previousBuild.getState()+"], "+build.getId()+" ["+build.getState()+"] is interesting to be inspected.");
                                        buildToBeInspected = new BuildToBeInspected(previousBuild, build, ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES, RepairnatorConfig.getInstance().getRunId());
                                    } else {
                                        LOGGER.debug("The pair "+previousBuild.getId()+" ["+previousBuild.getState()+"], "+build.getId()+" ["+build.getState()+"] is NOT interesting to be inspected.");
                                    }
                                }

                                if (buildToBeInspected != null) {
                                    this.rtScanner.submitBuildToExecution(buildToBeInspected);
                                }
                            } else {
                                LOGGER.debug("The previous build from "+build.getId()+" was not retrieved.");
                            }
                        }

                        try {
                            this.watchedBuildSerializer.serialize(build);
                        } catch (Throwable e) {
                            LOGGER.error("Error while serializing", e);
                        }

                        this.waitingBuilds.remove(build);
                        synchronized (this) {
                            this.nbSubmittedBuilds--;
                        }
                    }
                }
            }

            try {
                Thread.sleep(this.sleepTime * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("This will now stop.");
    }

    private static boolean thereIsDiffOnJavaFile(Build build, Build previousBuild) {
        GHCompare compare = getCompare(build, previousBuild);
        if (compare != null) {
            GHCommit.File[] modifiedFiles = compare.getFiles();
            for (GHCommit.File file : modifiedFiles) {
                if (file.getFileName().endsWith(".java") && !file.getFileName().toLowerCase().contains("test")) {
                    //System.out.println("First java file found: " + file.getFileName());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean thereIsDiffOnTests(Build build, Build previousBuild) {
        GHCompare compare = getCompare(build, previousBuild);
        if (compare != null) {
            GHCommit.File[] modifiedFiles = compare.getFiles();
            for (GHCommit.File file : modifiedFiles) {
                if (file.getFileName().toLowerCase().contains("test") && file.getFileName().endsWith(".java")) {
                    //System.out.println("First probable test file found: " + file.getFileName());
                    return true;
                }
            }
        }
        return false;
    }

    private static GHCompare getCompare(Build build, Build previousBuild) {
        try {
            GitHub gh = GitHubBuilder.fromEnvironment().build();

            GHRateLimit rateLimit = gh.getRateLimit();
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            //System.out.println("GitHub rate limit: Limit: " + rateLimit.limit + " - Remaining: " + rateLimit.remaining + " - Reset hour: " + dateFormat.format(rateLimit.reset));

            if (rateLimit.remaining > 2) {
                GHRepository ghRepo = gh.getRepository(build.getRepository().getSlug());
                GHCommit buildCommit = ghRepo.getCommit(build.getCommit().getSha());
                GHCommit previousBuildCommit = ghRepo.getCommit(previousBuild.getCommit().getSha());
                GHCompare compare = ghRepo.getCompare(previousBuildCommit, buildCommit);
                return compare;
            } else {
                System.out.println("You reached your rate limit for GitHub. You have to wait until " + dateFormat.format(rateLimit.reset) + " to get data. PRInformation will be null for build "+build.getId()+".");
            }
        } catch (IOException e) {
            System.out.println("Error while getting commit from GitHub: " + e);
        }
        return null;
    }
}
