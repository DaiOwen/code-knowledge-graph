package com.example.ckg.service.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CodeGraphOutputParser {

    /**
     * Parse codegraph_search output
     */
    public List<SymbolInfo> parseSearchResult(String output) {
        List<SymbolInfo> symbols = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "###\\s+(\\w+)\\s+\\((\\w+)\\)\\s*\\n" +
            "([^\\n]+):(\\d+)\\s*\\n" +
            "`([^`]+)`"
        );

        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            symbols.add(SymbolInfo.builder()
                .name(matcher.group(1))
                .type(matcher.group(2))
                .filePath(matcher.group(3).trim())
                .line(Integer.parseInt(matcher.group(4)))
                .signature(matcher.group(5))
                .build());
        }

        return symbols;
    }

    /**
     * Parse codegraph_callees output
     */
    public List<CallRelation> parseCalleesResult(String output, String callerName) {
        List<CallRelation> relations = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "-\\s+(\\w+)\\s+\\((\\w+)\\)\\s+-\\s+([^:]+):(\\d+)"
        );

        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            relations.add(CallRelation.builder()
                .callerName(callerName)
                .calleeName(matcher.group(1))
                .calleeType(matcher.group(2))
                .calleeFile(matcher.group(3).trim())
                .calleeLine(Integer.parseInt(matcher.group(4)))
                .build());
        }

        return relations;
    }

    /**
     * Parse codegraph_callers output
     */
    public List<CallRelation> parseCallersResult(String output, String calleeName) {
        List<CallRelation> relations = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "-\\s+(\\w+)\\s+\\((\\w+)\\)\\s+-\\s+([^:]+):(\\d+)"
        );

        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            relations.add(CallRelation.builder()
                .callerName(matcher.group(1))
                .callerType(matcher.group(2))
                .callerFile(matcher.group(3).trim())
                .callerLine(Integer.parseInt(matcher.group(4)))
                .calleeName(calleeName)
                .build());
        }

        return relations;
    }

    /**
     * Parse codegraph_node output
     */
    public NodeDetail parseNodeResult(String output) {
        // Extract location
        Pattern locationPattern = Pattern.compile("\\*\\*Location:\\*\\*\\s+([^:]+):(\\d+)");
        Matcher locationMatcher = locationPattern.matcher(output);

        String filePath = null;
        int line = 0;
        if (locationMatcher.find()) {
            filePath = locationMatcher.group(1).trim();
            line = Integer.parseInt(locationMatcher.group(2));
        }

        // Extract members
        List<MemberInfo> members = new ArrayList<>();
        Pattern memberPattern = Pattern.compile(
            "-\\s+(\\w+)\\s+\\((\\w+)\\):\\d+\\s+—\\s+`([^`]+)`"
        );
        Matcher memberMatcher = memberPattern.matcher(output);
        while (memberMatcher.find()) {
            members.add(MemberInfo.builder()
                .name(memberMatcher.group(1))
                .type(memberMatcher.group(2))
                .signature(memberMatcher.group(3))
                .build());
        }

        return NodeDetail.builder()
            .filePath(filePath)
            .line(line)
            .members(members)
            .build();
    }

    /**
     * Parse codegraph_files output
     */
    public List<FileInfo> parseFilesResult(String output) {
        List<FileInfo> files = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "-\\s+([^\\s]+)\\s+\\((\\w+),\\s+(\\d+)\\s+symbols\\)"
        );

        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            files.add(FileInfo.builder()
                .filePath(matcher.group(1))
                .language(matcher.group(2))
                .symbolCount(Integer.parseInt(matcher.group(3)))
                .build());
        }

        return files;
    }

    /**
     * Parse codegraph_explore output - extract call flow
     */
    public List<CallChainStep> parseExploreFlow(String output) {
        List<CallChainStep> steps = new ArrayList<>();

        // Parse numbered steps like "1. createOrder (file.java:8)"
        Pattern pattern = Pattern.compile(
            "(\\d+)\\.\\s+(\\w+)\\s+\\(([^:]+):(\\d+)\\)"
        );

        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            steps.add(CallChainStep.builder()
                .order(Integer.parseInt(matcher.group(1)))
                .symbolName(matcher.group(2))
                .filePath(matcher.group(3).trim())
                .lineNumber(Integer.parseInt(matcher.group(4)))
                .build());
        }

        return steps;
    }
}