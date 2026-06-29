package com.example.ckg.service.qa;

import com.example.ckg.dto.request.QARequest;
import com.example.ckg.dto.response.QAResponse;
import com.example.ckg.entity.ChatSession;
import com.example.ckg.entity.Message;
import com.example.ckg.repository.ChatSessionRepository;
import com.example.ckg.repository.MessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingQAService {

    private final StreamingChatLanguageModel streamingLlm;
    private final IntentClassifier intentClassifier;
    private final QueryTemplateMatcher templateMatcher;
    private final Neo4jExecutor neo4jExecutor;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    /**
     * Stream answer with callbacks
     */
    public void streamAnswer(QARequest request, Long userId, StreamingCallback callback) {
        String question = request.getQuestion();
        Long projectId = request.getProjectId();

        try {
            // Step 1: Classify intent
            IntentResult intent = intentClassifier.classify(question);

            // Step 2: Build context from graph and collect citation data
            CitationContext citationContext = buildContextWithCitations(intent, projectId);

            // Step 3: Build prompt
            String prompt = buildPrompt(question, citationContext.context);

            // Step 4: Stream answer
            StringBuilder fullAnswer = new StringBuilder();
            CompletableFuture<Void> streamingFuture = new CompletableFuture<>();

            streamingLlm.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(java.lang.String token) {
                    fullAnswer.append(token);
                    callback.onToken(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    // Build final response with actual citations
                    QAResponse qaResponse = QAResponse.builder()
                        .answer(fullAnswer.toString())
                        .citations(citationContext.citations)
                        .build();

                    // Save to history if session exists
                    if (request.getSessionId() != null) {
                        saveMessages(request.getSessionId(), userId, projectId, question, fullAnswer.toString());
                    }

                    callback.onComplete(qaResponse);
                    streamingFuture.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming error", error);
                    callback.onError(new Exception(error));
                    streamingFuture.completeExceptionally(error);
                }
            });

            // Wait for streaming to complete
            streamingFuture.get();

        } catch (InterruptedException e) {
            log.error("Streaming interrupted", e);
            Thread.currentThread().interrupt();
            callback.onError(new Exception("回答生成中断"));
        } catch (Exception e) {
            log.error("Streaming failed", e);
            callback.onError(e);
        }
    }

    /**
     * Build context and extract citations from graph query results
     */
    private CitationContext buildContextWithCitations(IntentResult intent, Long projectId) {
        StringBuilder context = new StringBuilder();
        List<QAResponse.Citation> citations = new ArrayList<>();

        // Match template and execute query
        var templateOpt = templateMatcher.match(intent);
        if (templateOpt.isPresent()) {
            var template = templateOpt.get();
            String entity = intent.getEntities().isEmpty() ? "" : intent.getEntities().get(0).getName();
            String cypher = template.getCypher()
                .replace("$entity", "\"" + escapeCypher(entity) + "\"")
                .replace("$projectId", String.valueOf(projectId));

            // Execute query
            List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

            if (!results.isEmpty()) {
                context.append("相关信息：\n");
                for (Map<String, Object> row : results) {
                    context.append("- ");
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        context.append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
                    }
                    context.append("\n");

                    // Extract citation from row
                    QAResponse.Citation citation = extractCitationFromRow(row);
                    if (citation != null) {
                        citations.add(citation);
                    }
                }
            } else {
                context.append("未找到相关信息。\n");
            }
        } else {
            // Try keyword matching using the intent type as fallback
            String questionText = intent.getIntent();
            var keywordTemplateOpt = templateMatcher.matchByKeywords(questionText);
            if (keywordTemplateOpt.isPresent()) {
                var template = keywordTemplateOpt.get();
                String cypher = template.getCypher().replace("$projectId", String.valueOf(projectId));
                List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

                if (!results.isEmpty()) {
                    context.append("相关结果：\n");
                    results.forEach(row -> {
                        context.append("- ").append(row).append("\n");

                        // Extract citation from row
                        QAResponse.Citation citation = extractCitationFromRow(row);
                        if (citation != null) {
                            citations.add(citation);
                        }
                    });
                }
            }
        }

        return new CitationContext(context.toString(), citations);
    }

    /**
     * Extract citation information from a query result row
     */
    private QAResponse.Citation extractCitationFromRow(Map<String, Object> row) {
        String filePath = null;
        Integer line = null;

        // Try to extract file path
        if (row.containsKey("filePath") && row.get("filePath") != null) {
            filePath = String.valueOf(row.get("filePath"));
        } else if (row.containsKey("file") && row.get("file") != null) {
            filePath = String.valueOf(row.get("file"));
        }

        // Try to extract line number
        if (row.containsKey("startLine") && row.get("startLine") != null) {
            line = toInteger(row.get("startLine"));
        } else if (row.containsKey("line") && row.get("line") != null) {
            line = toInteger(row.get("line"));
        }

        // Only create citation if we have a file path
        if (filePath != null && !filePath.isEmpty()) {
            return QAResponse.Citation.builder()
                .filePath(filePath)
                .line(line)
                .build();
        }

        return null;
    }

    /**
     * Convert object to Integer safely
     */
    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Escape special characters for Cypher query
     */
    private String escapeCypher(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildPrompt(String question, String context) {
        return String.format("""
            你是一个代码知识图谱问答助手。基于以下信息回答用户问题。

            ## 用户问题
            %s

            ## 图谱查询结果
            %s

            ## 输出要求
            1. 用简洁自然的语言回答
            2. 如果有具体的文件路径和行号，请引用
            3. 如果信息不足，诚实说明

            ## 回答
            """, question, context);
    }

    private void saveMessages(Long sessionId, Long userId, Long projectId,
                             String question, String answer) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            session = ChatSession.builder()
                .userId(userId)
                .projectId(projectId)
                .title(question.length() > 50 ? question.substring(0, 50) + "..." : question)
                .build();
            session = sessionRepository.save(session);
        }

        // Save user message
        Message userMsg = Message.builder()
            .sessionId(session.getId())
            .role(Message.MessageRole.USER)
            .content(question)
            .build();
        messageRepository.save(userMsg);

        // Save assistant message
        Message assistantMsg = Message.builder()
            .sessionId(session.getId())
            .role(Message.MessageRole.ASSISTANT)
            .content(answer)
            .build();
        messageRepository.save(assistantMsg);
    }

    /**
     * Callback interface for streaming responses
     */
    public interface StreamingCallback {
        void onToken(String token);
        void onComplete(QAResponse response);
        void onError(Exception e);
    }

    /**
     * Internal class to hold context and citations together
     */
    private static class CitationContext {
        final String context;
        final List<QAResponse.Citation> citations;

        CitationContext(String context, List<QAResponse.Citation> citations) {
            this.context = context;
            this.citations = citations;
        }
    }
}