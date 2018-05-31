package fr.inria.spirals.repairnator;

import fr.inria.spirals.repairnator.process.git.GitHelper;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {

    public static ProjectInspector mockProjectInspector(JobStatus jobStatus) {
        ProjectInspector inspector = mock(ProjectInspector.class);
        when(inspector.getJobStatus()).thenReturn(jobStatus);
        return inspector;
    }

    public static ProjectInspector mockProjectInspector(String localRepoPath, JobStatus jobStatus) {
        ProjectInspector inspector = mock(ProjectInspector.class);
        when(inspector.getRepoLocalPath()).thenReturn(localRepoPath);
        when(inspector.getJobStatus()).thenReturn(jobStatus);
        return inspector;
    }

    public static ProjectInspector mockProjectInspector(File tmpDir, BuildToBeInspected toBeInspected, JobStatus jobStatus) {
        ProjectInspector inspector = mock(ProjectInspector.class);
        when(inspector.getWorkspace()).thenReturn(tmpDir.getAbsolutePath());
        when(inspector.getRepoLocalPath()).thenReturn(tmpDir.getAbsolutePath()+"/repo");
        when(inspector.getRepoToPushLocalPath()).thenReturn(tmpDir.getAbsolutePath()+"/repotopush");
        when(inspector.getBuildToBeInspected()).thenReturn(toBeInspected);
        when(inspector.getBuggyBuild()).thenReturn(toBeInspected.getBuggyBuild());
        when(inspector.getPatchedBuild()).thenReturn(toBeInspected.getPatchedBuild());
        when(inspector.getM2LocalPath()).thenReturn(tmpDir.getAbsolutePath()+"/.m2");
        when(inspector.getJobStatus()).thenReturn(jobStatus);
        when(inspector.getGitHelper()).thenReturn(new GitHelper());
        return inspector;
    }

}
