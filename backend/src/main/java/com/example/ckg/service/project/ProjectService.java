package com.example.ckg.service.project;

import com.example.ckg.common.ErrorCode;
import com.example.ckg.dto.request.ProjectCreateRequest;
import com.example.ckg.dto.response.ProjectResponse;
import com.example.ckg.entity.GitCredential;
import com.example.ckg.entity.Project;
import com.example.ckg.entity.ParseTask;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.repository.GitCredentialRepository;
import com.example.ckg.repository.ParseTaskRepository;
import com.example.ckg.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final GitCredentialRepository credentialRepository;
    private final GitService gitService;

    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request, Long userId) {
        // 检查项目名是否已存在
        if (projectRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.PROJECT_EXISTS);
        }

        // 创建项目
        Project project = Project.builder()
            .name(request.getName())
            .gitUrl(request.getGitUrl())
            .branch(request.getBranch() != null ? request.getBranch() : "main")
            .language(request.getLanguage() != null ? request.getLanguage() : "java")
            .parseScope(request.getParseScope())
            .status(Project.ProjectStatus.PENDING)
            .build();

        // 处理凭证
        if (request.getCredentialId() != null) {
            GitCredential credential = credentialRepository.findById(request.getCredentialId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在"));
            if (!credential.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "无权使用该凭证");
            }
            project.setCredentialId(credential.getId());
        }

        project = projectRepository.save(project);

        // 创建解析任务
        ParseTask task = ParseTask.builder()
            .projectId(project.getId())
            .type(ParseTask.ParseType.FULL)
            .status(ParseTask.ParseStatus.PENDING)
            .progress(0)
            .build();
        parseTaskRepository.save(task);

        log.info("项目创建成功: projectId={}, name={}", project.getId(), project.getName());

        return toResponse(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 删除本地仓库
        if (project.getLocalPath() != null) {
            gitService.deleteLocalRepo(project.getLocalPath());
        }

        projectRepository.delete(project);
        log.info("项目删除成功: projectId={}", id);
    }

    @Transactional
    public ProjectResponse triggerParse(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getStatus() == Project.ProjectStatus.PARSING) {
            throw new BusinessException(ErrorCode.PROJECT_PARSING);
        }

        // 创建新的解析任务
        ParseTask task = ParseTask.builder()
            .projectId(projectId)
            .type(ParseTask.ParseType.FULL)
            .status(ParseTask.ParseStatus.PENDING)
            .progress(0)
            .build();
        parseTaskRepository.save(task);

        project.setStatus(Project.ProjectStatus.PARSING);
        projectRepository.save(project);

        log.info("触发项目解析: projectId={}", projectId);

        return toResponse(project);
    }

    public ParseTask getLatestParseTask(Long projectId) {
        return parseTaskRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
            .orElse(null);
    }

    public List<ParseTask> getParseTasks(Long projectId) {
        return parseTaskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
            .id(project.getId())
            .name(project.getName())
            .gitUrl(project.getGitUrl())
            .branch(project.getBranch())
            .language(project.getLanguage())
            .parseScope(project.getParseScope())
            .status(project.getStatus().name())
            .lastParsedAt(project.getLastParsedAt())
            .description(project.getDescription())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .build();
    }
}