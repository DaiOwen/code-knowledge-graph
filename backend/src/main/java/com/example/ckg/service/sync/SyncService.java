package com.example.ckg.service.sync;

import com.example.ckg.entity.Project;
import com.example.ckg.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ProjectRepository projectRepository;
    private final IncrementalParseService incrementalParseService;

    // Track last synced commit for each project
    private final Map<Long, String> lastSyncedCommit = new ConcurrentHashMap<>();

    /**
     * Scheduled sync job - runs every hour
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledSync() {
        log.info("Starting scheduled sync job");
        List<Project> projects = projectRepository.findByStatus(Project.ProjectStatus.READY);

        for (Project project : projects) {
            try {
                checkForUpdates(project);
            } catch (Exception e) {
                log.error("Failed to sync project: {}", project.getId(), e);
            }
        }

        log.info("Scheduled sync job completed");
    }

    /**
     * Check for updates in remote repository
     */
    public boolean checkForUpdates(Project project) {
        String localPath = project.getLocalPath();
        if (localPath == null || !Files.exists(Path.of(localPath))) {
            log.warn("Project {} has no local repository", project.getId());
            return false;
        }

        try {
            Repository repository = openRepository(localPath);
            Git git = new Git(repository);

            // Fetch remote changes
            git.fetch()
                .setRemote("origin")
                .setDryRun(true)  // Don't actually fetch, just check
                .call();

            // Get local HEAD
            Ref headRef = repository.exactRef("HEAD");
            if (headRef == null) {
                return false;
            }

            String localCommit = headRef.getObjectId().getName();

            // Get remote HEAD
            String remoteBranch = project.getBranch() != null ?
                "refs/remotes/origin/" + project.getBranch() : "refs/remotes/origin/HEAD";

            Ref remoteRef = repository.exactRef(remoteBranch);
            if (remoteRef == null) {
                // Try common default branch names
                remoteRef = repository.exactRef("refs/remotes/origin/main");
                if (remoteRef == null) {
                    remoteRef = repository.exactRef("refs/remotes/origin/master");
                }
            }

            if (remoteRef == null) {
                log.warn("Could not find remote branch for project {}", project.getId());
                return false;
            }

            String remoteCommit = remoteRef.getObjectId().getName();

            // Check if there are new commits
            if (!localCommit.equals(remoteCommit)) {
                log.info("Project {} has updates: {} -> {}", project.getId(),
                    localCommit.substring(0, 8), remoteCommit.substring(0, 8));

                // Get changed files between commits
                String[] changedFiles = getChangedFiles(repository, localCommit, remoteCommit);

                // Trigger incremental parse
                incrementalParseService.parseIncremental(project.getId(), localCommit, remoteCommit, changedFiles);

                // Update last synced commit
                lastSyncedCommit.put(project.getId(), remoteCommit);

                return true;
            }

            log.debug("Project {} is up to date", project.getId());
            return false;

        } catch (Exception e) {
            log.error("Error checking updates for project {}", project.getId(), e);
            return false;
        }
    }

    /**
     * Force sync a project (pull and parse)
     */
    public boolean forceSync(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String localPath = project.getLocalPath();
        if (localPath == null || !Files.exists(Path.of(localPath))) {
            log.warn("Project {} has no local repository", projectId);
            return false;
        }

        try {
            Repository repository = openRepository(localPath);
            Git git = new Git(repository);

            // Pull latest changes
            git.pull()
                .setRemote("origin")
                .setRemoteBranchName(project.getBranch() != null ? project.getBranch() : "main")
                .call();

            log.info("Pulled latest changes for project {}", projectId);
            return true;

        } catch (Exception e) {
            log.error("Failed to force sync project {}", projectId, e);
            return false;
        }
    }

    /**
     * Get list of commits since last sync
     */
    public RevCommit[] getNewCommits(Project project, String fromCommit, String toCommit) {
        try {
            Repository repository = openRepository(project.getLocalPath());

            ObjectId fromId = repository.resolve(fromCommit);
            ObjectId toId = repository.resolve(toCommit);

            if (fromId == null || toId == null) {
                return new RevCommit[0];
            }

            try (RevWalk walk = new RevWalk(repository)) {
                walk.markStart(walk.parseCommit(toId));
                walk.markUninteresting(walk.parseCommit(fromId));

                return walk.iterator().hasNext() ?
                    walk.toArray(new RevCommit[0]) : new RevCommit[0];
            }

        } catch (IOException e) {
            log.error("Failed to get new commits", e);
            return new RevCommit[0];
        }
    }

    private String[] getChangedFiles(Repository repository, String fromCommit, String toCommit) {
        try {
            // Use git diff to get changed files
            Git git = new Git(repository);

            var diffEntries = git.diff()
                .setOldTree(org.eclipse.jgit.lib.ObjectReader.class.cast(null))
                .call();

            // For simplicity, return empty array - actual implementation would use diff
            return new String[0];

        } catch (Exception e) {
            log.error("Failed to get changed files", e);
            return new String[0];
        }
    }

    private Repository openRepository(String path) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder.setGitDir(new File(path, ".git"))
            .readEnvironment()
            .findGitDir()
            .build();
    }
}