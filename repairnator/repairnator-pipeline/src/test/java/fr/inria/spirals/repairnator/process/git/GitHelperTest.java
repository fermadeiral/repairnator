package fr.inria.spirals.repairnator.process.git;

import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.Metrics;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class GitHelperTest {
    @Test
    public void testcomputePatchStats() throws GitAPIException, IOException {
        JobStatus jobStatus = new JobStatus("fakePomDirPath");

        String remoteRepo = "https://github.com/Spirals-Team/jtravis.git";
        String parentCommit = "2d65266f9a52b27f955ec9a74aa9ab4dac5537d7";
        String commit = "f267c73200e2ebb9431d6ffe80e507222567696c"; // GH says: 14 changed files, 443 additions, 104 deletions
        Path gitDir = java.nio.file.Files.createTempDirectory("jtravis");
        Git git = Git.cloneRepository().setURI(remoteRepo).setBranch("master").setDirectory(gitDir.toFile()).call();

        RevWalk revwalk = new RevWalk(git.getRepository());

        RevCommit revParentCommit = revwalk.parseCommit(ObjectId.fromString(parentCommit));
        RevCommit revCommit = revwalk.parseCommit(ObjectId.fromString(commit));

        GitHelper gitHelper = new GitHelper();
        Metrics metrics = jobStatus.getMetrics();
        gitHelper.computePatchStats(metrics, git, revCommit, revParentCommit);
        assertEquals(14, metrics.getPatchChangedFiles());
        assertEquals(443, metrics.getPatchAddedLines());
        assertEquals(104, metrics.getPatchDeletedLines());
    }
}
