package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.entity.ParseTask;
import com.example.ckg.entity.Project;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ParseTaskRepository;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.service.parse.ParseService;
import com.example.ckg.service.sync.SyncService;
import com.example.ckg.service.sync.IncrementalParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/parse")
@RequiredArgsConstructor
public class ParseController {

    private final ProjectRepository projectRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final ParseService parseService;
    private final SyncService syncService;
    private final IncrementalParseService incrementalParseService;

    /**
     * Trigger full parse for a project
     */
    @PostMapping("/full/{projectId}")
    public Result<ParseTask> triggerFullParse(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // Check if already parsing
        if (project.getStatus() == Project.ProjectStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目正在解析中");
        }

        // Create parse task
        ParseTask task = ParseTask.builder()
            .projectId(projectId)
            .type(ParseTask.ParseType.FULL)
            .status(ParseTask.ParseStatus.PENDING)
            .build();
        task = parseTaskRepository.save(task);

        // Update project status
        project.setStatus(Project.ProjectStatus.PARSING);
        projectRepository.save(project);

        // Trigger async parse
        parseService.parseProject(projectId, task.getId());

        log.info("Triggered full parse for project: {}", projectId);
        return Result.success(task);
    }

    /**
     * Trigger incremental parse for a project
     */
    @PostMapping("/incremental/{projectId}")
    public Result<ParseTask> triggerIncrementalParse(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getStatus() == Project.ProjectStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目正在解析中");
        }

        if (project.getStatus() != Project.ProjectStatus.READY) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目未完成首次解析，请先执行全量解析");
        }

        // Create incremental parse task
        ParseTask task = ParseTask.builder()
            .projectId(projectId)
            .type(ParseTask.ParseType.INCREMENTAL)
            .status(ParseTask.ParseStatus.PENDING)
            .build();
        task = parseTaskRepository.save(task);

        // Trigger incremental sync and parse
        syncService.forceSync(projectId);
        // incrementalParseService.parseIncremental would be called by SyncService

        log.info("Triggered incremental parse for project: {}", projectId);
        return Result.success(task);
    }

    /**
     * Get parse task status
     */
    @GetMapping("/task/{taskId}")
    public Result<ParseTask> getTaskStatus(@PathVariable Long taskId) {
        ParseTask task = parseTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "任务不存在"));
        return Result.success(task);
    }

    /**
     * Get all parse tasks for a project
     */
    @GetMapping("/tasks/{projectId}")
    public Result<List<ParseTask>> getProjectTasks(@PathVariable Long projectId) {
        return Result.success(parseTaskRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
    }

    /**
     * Cancel a running parse task
     */
    @PostMapping("/cancel/{taskId}")
    public Result<Void> cancelTask(@PathVariable Long taskId) {
        ParseTask task = parseTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "任务不存在"));

        if (task.getStatus() != ParseTask.ParseStatus.RUNNING &&
            task.getStatus() != ParseTask.ParseStatus.PENDING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务无法取消");
        }

        task.setStatus(ParseTask.ParseStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        parseTaskRepository.save(task);

        // Update project status
        Project project = projectRepository.findById(task.getProjectId()).orElse(null);
        if (project != null && project.getStatus() == Project.ProjectStatus.PARSING) {
            project.setStatus(Project.ProjectStatus.ERROR);
            projectRepository.save(project);
        }

        log.info("Cancelled parse task: {}", taskId);
        return Result.success();
    }

    /**
     * Get parse progress
     */
    @GetMapping("/progress/{projectId}")
    public Result<Map<String, Object>> getParseProgress(@PathVariable Long projectId) {
        ParseTask latestTask = parseTaskRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
            .orElse(null);

        if (latestTask == null) {
            return Result.success(Map.of(
                "status", "no_task",
                "progress", 0,
                "message", "暂无解析任务"
            ));
        }

        return Result.success(Map.of(
            "status", latestTask.getStatus().name(),
            "progress", latestTask.getProgress(),
            "type", latestTask.getType().name(),
            "startedAt", latestTask.getStartedAt() != null ? latestTask.getStartedAt().toString() : null,
            "completedAt", latestTask.getCompletedAt() != null ? latestTask.getCompletedAt().toString() : null,
            "errorMessage", latestTask.getErrorMessage() != null ? latestTask.getErrorMessage() : ""
        ));
    }

    /**
     * Force sync project (pull latest from remote)
     */
    @PostMapping("/sync/{projectId}")
    public Result<Map<String, Object>> forceSyncProject(@PathVariable Long projectId) {
        boolean success = syncService.forceSync(projectId);

        return Result.success(Map.of(
            "success", success,
            "message", success ? "同步成功" : "同步失败"
        ));
    }
}