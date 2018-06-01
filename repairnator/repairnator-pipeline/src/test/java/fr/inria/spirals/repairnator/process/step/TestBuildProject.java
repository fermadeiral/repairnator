package fr.inria.spirals.repairnator.process.step;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.TestUtils;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by urli on 07/03/2017.
 */
public class TestBuildProject {

    @Before
    public void setup() {
        Utils.setLoggersLevel(Level.ERROR);
    }

    @After
    public void tearDown() {
        RepairnatorConfig.deleteInstance();
    }

    @Test
    public void testBuildProject() throws IOException {
        long buildId = 207924136; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_build").toFile();
        tmpDir.deleteOnExit();

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        BuildProject buildStep = new BuildProject(inspector);

        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true)).setNextStep(buildStep);
        cloneStep.execute();

        assertThat(buildStep.isShouldStop(), is(false));
        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(3));
        StepStatus statusBuild = stepStatusList.get(2);
        assertThat(statusBuild.getStep(), is(buildStep));
        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }
    }

    @Test
    public void testBuildProjectWithPomNotInRoot() throws IOException {
        long buildId = 218036343;

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_build").toFile();
        tmpDir.deleteOnExit();

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        ProjectInspector inspector = new ProjectInspector(toBeInspected, tmpDir.getAbsolutePath(), Collections.EMPTY_LIST, Collections.EMPTY_LIST);

        JobStatus jobStatus = inspector.getJobStatus();

        CloneRepository cloneStep = new CloneRepository(inspector);
        BuildProject buildStep = new BuildProject(inspector);

        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true)).setNextStep(buildStep);
        cloneStep.execute();

        assertThat(buildStep.isShouldStop(), is(false));
        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(3));
        StepStatus statusBuild = stepStatusList.get(2);
        assertThat(statusBuild.getStep(), is(buildStep));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }
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
}
