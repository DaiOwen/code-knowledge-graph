package com.example.ckg.service.parse;

import com.example.ckg.entity.ParseTask;
import com.example.ckg.entity.Project;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ParseTaskRepository;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.service.project.CommitInfo;
import com.example.ckg.service.project.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseService {

    private final ProjectRepository projectRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final GitService gitService;
    private final McpClientService mcpClientService;
    private final CodeGraphOutputParser outputParser;
    private final Neo4jBatchWriter neo4jWriter;

    @Async
    @Transactional
    public void parseProject(Long projectId, Long taskId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        ParseTask task = parseTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "任务不存在"));

        try {
            // Update task status
            task.setStatus(ParseTask.ParseStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(5);
            parseTaskRepository.save(task);

            // Step 1: Clone repository (if not exists)
            log.info("Step 1: Clone repository for project {}", projectId);
            String localPath = project.getLocalPath();
            if (localPath == null || !Files.exists(Path.of(localPath))) {
                localPath = gitService.cloneRepository(
                    project.getGitUrl(),
                    project.getName(),
                    project.getBranch(),
                    null // TODO: Add credentials
                );
                project.setLocalPath(localPath);
                projectRepository.save(project);
            }
            task.setProgress(20);
            parseTaskRepository.save(task);

            // Step 2: Start codegraph MCP
            log.info("Step 2: Start codegraph MCP for project {}", projectId);
            mcpClientService.startCodegraph(projectId, Path.of(localPath));
            task.setProgress(30);
            parseTaskRepository.save(task);

            // Step 3: Parse code structure
            log.info("Step 3: Parse code structure");
            String exploreResult = mcpClientService.callExploreTool(
                projectId,
                "all classes and methods",
                100
            );
            task.setProgress(50);
            parseTaskRepository.save(task);

            // Step 4: Write to Neo4j
            log.info("Step 4: Write to Neo4j");
            List<CodeGraphResult.NodeData> nodes = parseNodes(exploreResult);
            List<CodeGraphResult.EdgeData> edges = parseEdges(exploreResult);

            // Separate class and method nodes
            List<CodeGraphResult.NodeData> classes = nodes.stream()
                .filter(n -> "class".equals(n.getType()) || "interface".equals(n.getType()))
                .collect(Collectors.toList());
            List<CodeGraphResult.NodeData> methods = nodes.stream()
                .filter(n -> "method".equals(n.getType()))
                .collect(Collectors.toList());

            neo4jWriter.writeClassNodes(projectId, classes);
            neo4jWriter.writeMethodNodes(projectId, methods);
            neo4jWriter.writeCallsEdges(projectId, edges);
            task.setProgress(70);
            parseTaskRepository.save(task);

            // Step 5: Parse git history
            log.info("Step 5: Parse git history");
            List<CommitInfo> commits = gitService.getCommitHistory(localPath, 1000);
            neo4jWriter.writeCommitNodes(projectId, commits);
            neo4jWriter.writeAuthorNodes(commits);
            task.setProgress(90);
            parseTaskRepository.save(task);

            // Step 6: Cleanup
            mcpClientService.stopCodegraph(projectId);

            // Update status
            task.setStatus(ParseTask.ParseStatus.COMPLETED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            project.setStatus(Project.ProjectStatus.READY);
            project.setLastParsedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Parse completed for project {}", projectId);

        } catch (Exception e) {
            log.error("Parse failed for project {}", projectId, e);
            task.setStatus(ParseTask.ParseStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            project.setStatus(Project.ProjectStatus.ERROR);
            projectRepository.save(project);

            mcpClientService.stopCodegraph(projectId);
        }
    }

    private List<CodeGraphResult.NodeData> parseNodes(String exploreResult) {
        // TODO: Implement actual parsing from codegraph output
        return List.of();
    }

    private List<CodeGraphResult.EdgeData> parseEdges(String exploreResult) {
        // TODO: Implement actual parsing from codegraph output
        return List.of();
    }
}