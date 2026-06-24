package com.example.ckg.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateRequest {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "Git URL 不能为空")
    private String gitUrl;

    private String branch;

    private String language;

    private String parseScope;

    private Long credentialId;

    private String description;
}