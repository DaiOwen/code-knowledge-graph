package com.example.ckg.service.project;

import com.example.ckg.entity.Project;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    @Value("${git.repos-path:./repos}")
    private String reposPath;

    /**
     * Clone Git repository
     */
    public String cloneRepository(String gitUrl, String projectName, String branch,
                                   CredentialsProvider credentials) throws GitAPIException {
        Path localPath = Paths.get(reposPath, sanitizeName(projectName));

        // Create directory if not exists
        try {
            Files.createDirectories(localPath.getParent());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.GIT_CLONE_FAILED, "无法创建仓库目录");
        }

        // Clone repository
        Git git = Git.cloneRepository()
            .setURI(gitUrl)
            .setDirectory(localPath.toFile())
            .setBranch(branch)
            .setCredentialsProvider(credentials)
            .setCloneSubmodules(false)
            .call();

        git.close();
        log.info("Git仓库克隆成功: {} -> {}", gitUrl, localPath);

        return localPath.toString();
    }

    /**
     * Pull latest changes
     */
    public void pullRepository(String localPath, CredentialsProvider credentials) throws GitAPIException, IOException {
        Git git = Git.open(new File(localPath));
        git.pull()
            .setCredentialsProvider(credentials)
            .call();
        git.close();
        log.info("Git仓库更新成功: {}", localPath);
    }

    /**
     * Get changed files between two commits
     */
    public List<String> getChangedFiles(String localPath, String oldCommit, String newCommit) throws IOException, GitAPIException {
        Git git = Git.open(new File(localPath));

        List<String> changedFiles = new ArrayList<>();
        git.diff()
            .setOldTree(git.revParse(oldCommit))
            .setNewTree(git.revParse(newCommit))
            .call()
            .forEach(entry -> changedFiles.add(entry.getNewPath()));

        git.close();
        return changedFiles;
    }

    /**
     * Get current HEAD commit hash
     */
    public String getCurrentCommitHash(String localPath) throws IOException {
        Git git = Git.open(new File(localPath));
        Ref head = git.getRepository().findRef("HEAD");
        String hash = head.getObjectId().getName();
        git.close();
        return hash;
    }

    /**
     * Get commit history
     */
    public List<CommitInfo> getCommitHistory(String localPath, int maxCount) throws IOException, GitAPIException {
        Git git = Git.open(new File(localPath));

        List<CommitInfo> commits = new ArrayList<>();
        Iterable<RevCommit> log = git.log().setMaxCount(maxCount).call();

        for (RevCommit commit : log) {
            commits.add(CommitInfo.builder()
                .hash(commit.getId().getName())
                .message(commit.getFullMessage())
                .authorName(commit.getAuthorIdent().getName())
                .authorEmail(commit.getAuthorIdent().getEmailAddress())
                .authoredAt(LocalDateTime.ofInstant(
                    commit.getAuthorIdent().getWhen().toInstant(),
                    ZoneId.systemDefault()))
                .build());
        }

        git.close();
        return commits;
    }

    /**
     * Get last commit for a specific file
     */
    public CommitInfo getLastCommitForFile(String localPath, String filePath) throws IOException, GitAPIException {
        Git git = Git.open(new File(localPath));

        Iterable<RevCommit> log = git.log()
            .addPath(filePath)
            .setMaxCount(1)
            .call();

        RevCommit commit = log.iterator().next();
        git.close();

        if (commit == null) {
            return null;
        }

        return CommitInfo.builder()
            .hash(commit.getId().getName())
            .message(commit.getFullMessage())
            .authorName(commit.getAuthorIdent().getName())
            .authorEmail(commit.getAuthorIdent().getEmailAddress())
            .authoredAt(LocalDateTime.ofInstant(
                commit.getAuthorIdent().getWhen().toInstant(),
                ZoneId.systemDefault()))
            .build();
    }

    /**
     * Delete local repository
     */
    public void deleteLocalRepo(String localPath) {
        try {
            Files.walk(Paths.get(localPath))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            log.info("本地仓库已删除: {}", localPath);
        } catch (IOException e) {
            log.warn("删除本地仓库失败: {}", localPath, e);
        }
    }

    /**
     * Create credentials provider
     */
    public CredentialsProvider createCredentials(String username, String password) {
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}