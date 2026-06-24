package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.dto.request.ProjectCreateRequest;
import com.example.ckg.dto.response.ProjectResponse;
import com.example.ckg.entity.ParseTask;
import com.example.ckg.service.project.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<List<ProjectResponse>> listProjects() {
        return Result.success(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public Result<ProjectResponse> getProject(@PathVariable Long id) {
        return Result.success(projectService.getProjectById(id));
    }

    @PostMapping
    public Result<ProjectResponse> createProject(
        @Valid @RequestBody ProjectCreateRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        return Result.success(projectService.createProject(request, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }

    @PostMapping("/{id}/parse")
    public Result<ProjectResponse> triggerParse(@PathVariable Long id) {
        return Result.success(projectService.triggerParse(id));
    }

    @GetMapping("/{id}/tasks")
    public Result<List<ParseTask>> getParseTasks(@PathVariable Long id) {
        return Result.success(projectService.getParseTasks(id));
    }

    @GetMapping("/{id}/tasks/latest")
    public Result<ParseTask> getLatestTask(@PathVariable Long id) {
        return Result.success(projectService.getLatestParseTask(id));
    }
}