package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileInfo {
    private String filePath;
    private String language;
    private Integer symbolCount;
}