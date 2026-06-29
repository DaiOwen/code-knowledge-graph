package com.example.ckg.service.qa;

import com.example.ckg.dto.request.QARequest;
import com.example.ckg.dto.response.QAResponse;
import com.example.ckg.entity.ChatSession;
import com.example.ckg.entity.Message;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ChatSessionRepository;
import com.example.ckg.repository.MessageRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final ChatLanguageModel llm;
    private final Neo4jClient neo4jClient;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final IntentClassifier intentClassifier;
    private final QueryTemplateMatcher templateMatcher;
    private final Neo4jExecutor neo4jExecutor;
    private final AnswerGenerator answerGenerator;

    // Allowed characters for entity names (alphanumeric, underscore, dot, dollar sign - valid Java identifier chars)
    private static final String ENTITY_NAME_PATTERN = "^[a-zA-Z_$][a-zA-Z0-9_$]*$";

    @Transactional
    public QAResponse ask(QARequest request, Long userId) {
        String question = request.getQuestion();
        Long projectId = request.getProjectId();
        Long sessionId = request.getSessionId();

        // Validate request
        validateRequest(request);

        // Step 1: Classify intent
        log.info("Classifying intent for question: {}", question);
        IntentResult intent = intentClassifier.classify(question);

        // Step 2: Match template or generate Cypher
        String cypher;
        Optional<QueryTemplateMatcher.QueryTemplate> templateOpt = templateMatcher.match(intent);

        if (templateOpt.isPresent()) {
            QueryTemplateMatcher.QueryTemplate template = templateOpt.get();
            String entity = intent.getEntities().isEmpty() ? "" : intent.getEntities().get(0).getName();

            // Validate entity name to prevent Cypher injection
            validateEntityName(entity);

            cypher = template.getCypher()
                .replace("$entity", "\"" + escapeCypherString(entity) + "\"")
                .replace("$projectId", String.valueOf(projectId));
        } else {
            // Fallback: keyword matching
            templateOpt = templateMatcher.matchByKeywords(question);
            if (templateOpt.isPresent()) {
                cypher = templateOpt.get().getCypher()
                    .replace("$projectId", String.valueOf(projectId))
                    .replace("$entity", "\"\"");
            } else {
                // No template matched, return generic response
                return QAResponse.builder()
                    .answer("抱歉，我无法理解这个问题。请尝试使用更明确的表述，例如：\n" +
                           "- \"谁调用了 XXX 方法？\"\n" +
                           "- \"修改 XXX 会影响什么？\"\n" +
                           "- \"XXX 方法是谁写的？\"")
                    .citations(Collections.emptyList())
                    .build();
            }
        }

        // Step 3: Execute Cypher
        log.info("Executing Cypher: {}", cypher);
        GraphResult graphResult = neo4jExecutor.execute(cypher, projectId);

        // Step 4: Generate answer
        log.info("Generating answer");
        String answer = answerGenerator.generate(question, graphResult);

        // Step 5: Build citations
        List<QAResponse.Citation> citations = buildCitations(graphResult);

        // Step 6: Save message history
        if (sessionId != null) {
            saveMessage(sessionId, userId, projectId, question, answer, citations);
        }

        return QAResponse.builder()
            .answer(answer)
            .citations(citations)
            .build();
    }

    /**
     * Validate the QA request
     */
    private void validateRequest(QARequest request) {
        if (request.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目ID不能为空");
        }
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "问题不能为空");
        }
        if (request.getQuestion().length() > 1000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "问题长度不能超过1000个字符");
        }
    }

    /**
     * Validate entity name to prevent Cypher injection
     */
    private void validateEntityName(String entity) {
        if (entity == null || entity.isEmpty()) {
            return; // Empty entity is allowed
        }

        // Check for valid Java identifier pattern
        // Allow qualified names like "ClassName.methodName"
        String[] parts = entity.split("\\.");
        for (String part : parts) {
            if (!part.matches(ENTITY_NAME_PATTERN)) {
                log.warn("Invalid entity name detected: {}", entity);
                throw new BusinessException(ErrorCode.CYPHER_INJECTION, "无效的方法名或类名格式");
            }
        }

        // Additional check for dangerous keywords
        String lowerEntity = entity.toLowerCase();
        String[] dangerousKeywords = {"match", "create", "delete", "set", "merge", "remove", "drop", "call", "load", "with"};
        for (String keyword : dangerousKeywords) {
            if (lowerEntity.equals(keyword)) {
                throw new BusinessException(ErrorCode.CYPHER_INJECTION, "实体名包含保留关键字");
            }
        }
    }

    /**
     * Escape special characters for safe Cypher string literal
     */
    private String escapeCypherString(String str) {
        if (str == null) return "";
        // Escape backslash first, then quotes
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("'", "\\'");
    }

    /**
     * Build citations from graph result
     */
    private List<QAResponse.Citation> buildCitations(GraphResult graphResult) {
        if (graphResult == null || graphResult.getNodes() == null) {
            return Collections.emptyList();
        }

        return graphResult.getNodes().stream()
            .filter(n -> n.getProperties() != null && n.getProperties().containsKey("filePath"))
            .map(n -> {
                Integer line = null;
                if (n.getProperties().containsKey("startLine") && n.getProperties().get("startLine") != null) {
                    Object lineObj = n.getProperties().get("startLine");
                    if (lineObj instanceof Number) {
                        line = ((Number) lineObj).intValue();
                    }
                }

                return QAResponse.Citation.builder()
                    .filePath((String) n.getProperties().get("filePath"))
                    .line(line)
                    .build();
            })
            .distinct()
            .collect(Collectors.toList());
    }

    private void saveMessage(Long sessionId, Long userId, Long projectId,
                             String question, String answer, List<QAResponse.Citation> citations) {
        // Create session if not exists
        ChatSession session;
        if (sessionId == null) {
            session = ChatSession.builder()
                .userId(userId)
                .projectId(projectId)
                .title(generateTitle(question))
                .build();
            session = sessionRepository.save(session);
        } else {
            session = sessionRepository.findById(sessionId).orElse(null);
        }

        if (session != null) {
            // Save user message
            Message userMsg = Message.builder()
                .sessionId(session.getId())
                .role(Message.MessageRole.USER)
                .content(question)
                .build();
            messageRepository.save(userMsg);

            // Save assistant message
            String citationsJson = citations.isEmpty() ? null :
                citations.stream()
                    .map(c -> String.format("{\"file\":\"%s\",\"line\":%d}",
                        c.getFilePath(), c.getLine() != null ? c.getLine() : 0))
                    .collect(Collectors.joining(",", "[", "]"));

            Message assistantMsg = Message.builder()
                .sessionId(session.getId())
                .role(Message.MessageRole.ASSISTANT)
                .content(answer)
                .citations(citationsJson)
                .build();
            messageRepository.save(assistantMsg);
        }
    }

    private String generateTitle(String question) {
        if (question.length() <= 50) {
            return question;
        }
        return question.substring(0, 50) + "...";
    }
}