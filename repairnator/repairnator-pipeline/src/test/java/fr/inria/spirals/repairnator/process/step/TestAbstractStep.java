package fr.inria.spirals.repairnator.process.step;

import ch.qos.logback.classic.Level;
import fr.inria.spirals.repairnator.TestUtils;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by urli on 21/02/2017.
 */
public class TestAbstractStep {

    public class AbstractStepNop extends AbstractStep {

        public AbstractStepNop(ProjectInspector inspector) {
            super(inspector, false);
        }

        @Override
        protected StepStatus businessExecute() {
            return StepStatus.buildSuccess(this);
        }
    }

    @Before
    public void setup() {
        Utils.setLoggersLevel(Level.ERROR);
    }

    @After
    public void tearDown() {
        RepairnatorConfig.deleteInstance();
    }

    @Test
    public void testSetPropertiesWillGivePropertiesToOtherSteps() {
        JobStatus jobStatus = new JobStatus("");
        ProjectInspector mockInspector = TestUtils.mockProjectInspector(jobStatus);

        AbstractStep step1 = new AbstractStepNop(mockInspector);
        AbstractStep step2 = new AbstractStepNop(mockInspector);
        AbstractStep step3 = new AbstractStepNop(mockInspector);

        Properties properties = new Properties();
        properties.setProperty("testvalue", "toto");
        properties.setProperty("anotherone","foo");

        step1.setNextStep(step2).setNextStep(step3);
        step1.setProperties(properties);

        assertThat(step3.getProperties(), is(properties));
    }

    @Test
    public void testGetPomOnSimpleProject() {
        String localRepoPath = "./src/test/resources/test-abstractstep/simple-maven-project";
        JobStatus jobStatus = new JobStatus(localRepoPath);
        ProjectInspector mockInspector = TestUtils.mockProjectInspector(localRepoPath, jobStatus);

        AbstractStep step1 = new AbstractStepNop(mockInspector);

        String expectedPomPath = localRepoPath+"/pom.xml";

        assertThat(step1.getPom(), is(expectedPomPath));
    }

    @Test
    public void testGetPomWhenNotFoundShouldSetStopFlag() {
        String localRepoPath = "./unkown-path";
        JobStatus jobStatus = new JobStatus(localRepoPath);
        ProjectInspector mockInspector = TestUtils.mockProjectInspector(localRepoPath, jobStatus);

        AbstractStep step1 = new AbstractStepNop(mockInspector);

        String expectedPomPath = localRepoPath+"/pom.xml";

        // return this path but set the flag to stop
        assertThat(step1.getPom(), is(expectedPomPath));
        assertThat(step1.isShouldStop(), is(true));
    }

    @Test
    public void testGetPomWithComplexMavenProjectShouldSetRepoPath() {
        String localRepoPath = "./src/test/resources/test-abstractstep/complex-maven-project";
        JobStatus jobStatus = new JobStatus(localRepoPath);
        ProjectInspector mockInspector = TestUtils.mockProjectInspector(localRepoPath, jobStatus);

        AbstractStep step1 = new AbstractStepNop(mockInspector);

        String expectedPomPath = localRepoPath+"/a-submodule";

        String obtainedPom = step1.getPom();
        assertThat(jobStatus.getPomDirPath(), is(expectedPomPath));
    }

}
