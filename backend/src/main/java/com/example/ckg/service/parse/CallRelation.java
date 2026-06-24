package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallRelation {
    private String callerName;
    private String callerType;
    private String callerFile;
    private Integer callerLine;
    private String calleeName;
    private String calleeType;
    private String calleeFile;
    private Integer calleeLine;
}