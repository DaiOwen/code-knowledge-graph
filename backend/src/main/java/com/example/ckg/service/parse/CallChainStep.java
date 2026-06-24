package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallChainStep {
    private Integer order;
    private String symbolName;
    private String filePath;
    private Integer lineNumber;
}