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
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldPass;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;
import fr.inria.spirals.repairnator.process.testinformation.FailureType;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.PipelineState;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by urli on 07/03/2017.
 */
public class TestGatherTestInformation {

    @Before
    public void setup() {
        Utils.setLoggersLevel(Level.ERROR);
    }

    @After
    public void tearDown() {
        RepairnatorConfig.deleteInstance();
    }

    @Test
    public void testGatherTestInformationWhenFailing() throws IOException {
        long buildId = 207890790; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        tmpDir.deleteOnExit();
        System.out.println("Dirpath : "+tmpDir.getAbsolutePath());

        File repoDir = new File(tmpDir, "repo");
        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");


        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true))
                .setNextStep(new TestProject(inspector))
                .setNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(4));
        StepStatus gatherTestInfoStatus = stepStatusList.get(3);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(repoDir.getCanonicalPath()));
        assertThat(gatherTestInformation.getNbTotalTests(), is(98));
        assertThat(gatherTestInformation.getNbFailingTests(), is(26));
        assertThat(gatherTestInformation.getNbErroringTests(), is(5));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        Set<String> failureNames = jobStatus.getMetrics().getFailureNames();

        assertThat(failureNames.contains("java.lang.StringIndexOutOfBoundsException"), is(true));
        assertThat(failureNames.size(), is(5));

        assertThat(jobStatus.getFailureLocations().size(), is(10));
    }

    @Test
    public void testGatherTestInformationOnlyOneErroring() throws IOException {
        long buildId = 208897371; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        tmpDir.deleteOnExit();

        File repoDir = new File(tmpDir, "repo");
        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");


        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true)).setNextStep(new TestProject(inspector)).setNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(4));
        StepStatus gatherTestInfoStatus = stepStatusList.get(3);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(repoDir.getCanonicalPath()));
        assertThat(gatherTestInformation.getNbTotalTests(), is(8));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(1));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        Set<String> failureNames = jobStatus.getMetrics().getFailureNames();

        assertThat("failure names"+ StringUtils.join(failureNames.toArray()), failureNames.contains("java.lang.StringIndexOutOfBoundsException"), is(true));
        assertThat(failureNames.size(), is(1));

        assertThat(jobStatus.getFailureLocations().size(), is(1));

        FailureLocation expectedFailureLocation = new FailureLocation("nopol_examples.nopol_example_1.NopolExampleTest");
        FailureType failureType = new FailureType("java.lang.StringIndexOutOfBoundsException", "String index out of range: -5", true);
        expectedFailureLocation.addFailure(failureType);
        expectedFailureLocation.addErroringMethod("test5");

        FailureLocation actualLocation = jobStatus.getFailureLocations().iterator().next();

        assertThat(actualLocation, is(expectedFailureLocation));
    }

    @Test
    public void testGatherTestInformationWhenErroring() throws IOException {
        long buildId = 208240908; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        tmpDir.deleteOnExit();
        System.out.println("Dirpath : "+tmpDir.getAbsolutePath());

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");


        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true)).setNextStep(new TestProject(inspector)).setNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(4));
        StepStatus gatherTestInfoStatus = stepStatusList.get(3);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(tmpDir.getAbsolutePath()+"/repo"));
        assertThat(gatherTestInformation.getNbTotalTests(), is(26));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(5));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        Set<String> failureNames = jobStatus.getMetrics().getFailureNames();

        assertThat("Got the following errors: "+StringUtils.join(failureNames, ","), failureNames.contains("java.lang.NullPointerException"), is(true));
        assertThat(failureNames.size(), is(3));

        assertThat(jobStatus.getFailureLocations().size(), is(4));
    }

    @Test
    public void testGatherTestInformationWhenNotFailing() throws IOException {
        long buildId = 201176013; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        tmpDir.deleteOnExit();
        System.out.println("Dirpath : "+tmpDir.getAbsolutePath());

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true)).setNextStep(new TestProject(inspector)).setNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(4));
        StepStatus gatherTestInfoStatus = stepStatusList.get(3);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            if (stepStatus.getStep() != gatherTestInformation) {
                assertThat(stepStatus.isSuccess(), is(true));
            } else {
                assertThat(stepStatus.isSuccess(), is(false));
            }

        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is(PipelineState.NOTFAILING.name()));

        assertThat(jobStatus.getFailingModulePath(), is(tmpDir.getAbsolutePath()+"/repo"));
        assertThat(gatherTestInformation.getNbTotalTests(), is(1));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(0));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        Set<String> failureNames = jobStatus.getMetrics().getFailureNames();
        assertThat(failureNames.size(), is(0));
        assertThat(jobStatus.getFailureLocations().size(), is(0));
    }

    @Test
    public void testGatherTestInformationWhenNotFailingWithPassingContract() throws IOException {
        long buildId = 201176013; // surli/failingProject build

        Build build = this.checkBuildAndReturn(buildId, false);

        File tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        tmpDir.deleteOnExit();
        System.out.println("Dirpath : "+tmpDir.getAbsolutePath());

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = TestUtils.mockProjectInspector(tmpDir, toBeInspected, jobStatus);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldPass(), false);


        cloneStep.setNextStep(new CheckoutBuggyBuild(inspector, true))
                .setNextStep(new TestProject(inspector))
                .setNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(4));
        StepStatus gatherTestInfoStatus = stepStatusList.get(3);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        assertThat(jobStatus.getFailingModulePath(), is(tmpDir.getAbsolutePath()+"/repo"));
        assertThat(gatherTestInformation.getNbTotalTests(), is(1));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(0));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        Set<String> failureNames = jobStatus.getMetrics().getFailureNames();
        assertThat(failureNames.size(), is(0));
        assertThat(jobStatus.getFailureLocations().size(), is(0));
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
