package fr.inria.spirals.repairnator.process.inspectors;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;
import fr.inria.spirals.repairnator.notifier.BugAndFixerBuildsNotifier;
import fr.inria.spirals.repairnator.notifier.engines.NotifierEngine;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.process.step.push.PushProcessEnd;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorSerializer4Bears;
import fr.inria.spirals.repairnator.serializer.SerializerType;
import fr.inria.spirals.repairnator.serializer.engines.SerializedData;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.states.PipelineState;
import fr.inria.spirals.repairnator.states.PushState;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by urli on 24/04/2017.
 */
public class TestProjectInspector4Bears {

    private static final String SOLVER_PATH_DIR = "src/test/resources/z3/";
    private static final String SOLVER_NAME_LINUX = "z3_for_linux";
    private static final String SOLVER_NAME_MAC = "z3_for_mac";

    private SerializerEngine serializerEngine;
    private List<AbstractDataSerializer> serializers;
    private List<AbstractNotifier> notifiers;
    private NotifierEngine notifierEngine;

    @Before
    public void setUp() {
        String solverPath;
        if (isMac()) {
            solverPath = SOLVER_PATH_DIR+SOLVER_NAME_MAC;
        } else {
            solverPath = SOLVER_PATH_DIR+SOLVER_NAME_LINUX;
        }

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.BEARS);
        config.setZ3solverPath(solverPath);
        config.setPush(true);
        config.setPushRemoteRepo("");
        Utils.setLoggersLevel(Level.ERROR);

        serializers = new ArrayList<>();
        List<SerializerEngine> serializerEngines = new ArrayList<>();
        serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);
        serializers.add(new InspectorSerializer4Bears(serializerEngines));

        notifiers = new ArrayList<>();
        List<NotifierEngine> notifierEngines = new ArrayList<>();
        notifierEngine = mock(NotifierEngine.class);
        notifierEngines.add(notifierEngine);
        notifiers.add(new BugAndFixerBuildsNotifier(notifierEngines));
    }

    public static boolean isMac() {
        String OS = System.getProperty("os.name").toLowerCase();
        return (OS.contains("mac"));
    }

    @After
    public void tearDown() {
        RepairnatorConfig.deleteInstance();
    }

    private void checkStepStatus(List<StepStatus> statuses, Map<Class<? extends AbstractStep>,StepStatus.StatusKind> expectedValues) {
        for (StepStatus stepStatus : statuses) {
            if (!expectedValues.containsKey(stepStatus.getStep().getClass())) {
                assertThat("Step failing: "+stepStatus, stepStatus.isSuccess(), is(true));
            } else {
                StepStatus.StatusKind expectedStatus = expectedValues.get(stepStatus.getStep().getClass());
                assertThat("Status was not as expected" + stepStatus, stepStatus.getStatus(), is(expectedStatus));
                expectedValues.remove(stepStatus.getStep().getClass());
            }
        }

        assertThat(expectedValues.isEmpty(), is(true));
    }

    private Build checkBuildAndReturn(long buildId, boolean isPR) {
        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build build = optionalBuild.get();
        assertThat(build, notNullValue());
        assertThat(buildId, is(build.getId()));
        assertThat(build.isPullRequest(), is(isPR));
        return build;
    }

    @Test
    public void testFailingPassingProject() throws IOException, GitAPIException {
        long buildIdFailing = 203797975;
        long buildIdPassing = 203800961;

        File tmpDir = Files.createTempDirectory("test_bears1").toFile();
        tmpDir.deleteOnExit();

        Build failingBuild = this.checkBuildAndReturn(buildIdFailing, false);
        Build passingBuild = this.checkBuildAndReturn(buildIdPassing, false);
        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, passingBuild, ScannedBuildStatus.FAILING_AND_PASSING, "test");

        ProjectInspector4Bears inspector = new ProjectInspector4Bears(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        List<StepStatus> stepStatusList = inspector.getJobStatus().getStepStatuses();

        Map<Class<? extends AbstractStep>, StepStatus.StatusKind> expectedStatuses = new HashMap<>();
        expectedStatuses.put(PushProcessEnd.class, StepStatus.StatusKind.SKIPPED); // no remote info provided

        this.checkStepStatus(stepStatusList, expectedStatuses);

        assertThat(jobStatus.getLastPushState(), is(PushState.REPO_NOT_PUSHED));

        assertThat(inspector.isBug(), is(true));
        assertThat(inspector.getBugType(), is("BUG_FAILING_PASSING"));
        assertThat(jobStatus.getFailureLocations().size(), is(1));
        assertThat(jobStatus.getMetrics().getFailureNames().size(), is(1));

        String finalStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(finalStatus, is(PipelineState.BUG_FAILING_PASSING.name()));

        verify(notifierEngine, times(1)).notify(anyString(), anyString());
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR4BEARS));

        Git gitDir = Git.open(new File(inspector.getRepoToPushLocalPath()));
        Iterable<RevCommit> logs = gitDir.log().call();

        Iterator<RevCommit> iterator = logs.iterator();
        assertThat(iterator.hasNext(), is(true));

        RevCommit commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("End of the repairnator process"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Human patch"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Bug commit"));

        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPassingPassingProject() throws IOException, GitAPIException {
        long buildIdPreviousPassing = 201938325;
        long buildIdPassing = 201938881;

        File tmpDir = Files.createTempDirectory("test_bears2").toFile();
        tmpDir.deleteOnExit();

        Build previousPassingBuild = this.checkBuildAndReturn(buildIdPreviousPassing, false);
        Build passingBuild = this.checkBuildAndReturn(buildIdPassing, false);
        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(previousPassingBuild, passingBuild, ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES, "test");

        ProjectInspector4Bears inspector = new ProjectInspector4Bears(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        List<StepStatus> stepStatusList = inspector.getJobStatus().getStepStatuses();

        Map<Class<? extends AbstractStep>, StepStatus.StatusKind> expectedStatuses = new HashMap<>();
        expectedStatuses.put(PushProcessEnd.class, StepStatus.StatusKind.SKIPPED); // no remote info provided

        this.checkStepStatus(stepStatusList, expectedStatuses);

        assertThat(jobStatus.getLastPushState(), is(PushState.REPO_NOT_PUSHED));

        assertThat(inspector.isBug(), is(true));
        assertThat(inspector.getBugType(), is("BUG_PASSING_PASSING"));
        assertThat(jobStatus.getFailureLocations().size(), is(1));
        assertThat(jobStatus.getMetrics().getFailureNames().size(), is(1));

        String finalStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(finalStatus, is(PipelineState.BUG_PASSING_PASSING.name()));

        verify(notifierEngine, times(1)).notify(anyString(), anyString());
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR4BEARS));

        Git gitDir = Git.open(new File(inspector.getRepoToPushLocalPath()));
        Iterable<RevCommit> logs = gitDir.log().call();

        Iterator<RevCommit> iterator = logs.iterator();
        assertThat(iterator.hasNext(), is(true));

        RevCommit commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("End of the repairnator process"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Human patch"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Bug commit"));

        assertThat(iterator.hasNext(), is(false));
    }
}
