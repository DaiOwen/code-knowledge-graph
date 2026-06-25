package com.example.ckg.service.qa;

import com.example.ckg.dto.request.QARequest;
import com.example.ckg.dto.response.QAResponse;
import com.example.ckg.entity.ChatSession;
import com.example.ckg.entity.Message;
import com.example.ckg.repository.ChatSessionRepository;
import com.example.ckg.repository.MessageRepository;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

            // Step 2: Build context from graph
            String context = buildContext(intent, projectId);

            // Step 3: Build prompt
            String prompt = buildPrompt(question, context);

            // Step 4: Stream answer
            StringBuilder fullAnswer = new StringBuilder();
            CompletableFuture<Void> streamingFuture = new CompletableFuture<>();

            streamingLlm.chat(prompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullAnswer.append(partialResponse);
                    callback.onToken(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    // Build final response
                    QAResponse response = QAResponse.builder()
                        .answer(fullAnswer.toString())
                        .citations(buildCitations(intent, projectId))
                        .build();

                    // Save to history if session exists
                    if (request.getSessionId() != null) {
                        saveMessages(request.getSessionId(), userId, projectId, question, fullAnswer.toString());
                    }

                    callback.onComplete(response);
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

        } catch (InterruptedException | ExecutionException e) {
            log.error("Streaming interrupted", e);
            callback.onError(new Exception("回答生成中断"));
        } catch (Exception e) {
            log.error("Streaming failed", e);
            callback.onError(e);
        }
    }

    private String buildContext(IntentResult intent, Long projectId) {
        StringBuilder context = new StringBuilder();

        // Match template and execute query
        var templateOpt = templateMatcher.match(intent);
        if (templateOpt.isPresent()) {
            var template = templateOpt.get();
            String entity = intent.getEntities().isEmpty() ? "" : intent.getEntities().get(0).getName();
            String cypher = template.getCypher()
                .replace("$entity", "\"" + entity + "\"")
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
                }
            } else {
                context.append("未找到相关信息。\n");
            }
        } else {
            // Try keyword matching
            var keywordTemplateOpt = templateMatcher.matchByKeywords(intent.getQuestion());
            if (keywordTemplateOpt.isPresent()) {
                var template = keywordTemplateOpt.get();
                String cypher = template.getCypher().replace("$projectId", String.valueOf(projectId));
                List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

                if (!results.isEmpty()) {
                    context.append("相关结果：\n");
                    results.forEach(row -> {
                        context.append("- ").append(row).append("\n");
                    });
                }
            }
        }

        return context.toString();
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

    private List<QAResponse.Citation> buildCitations(IntentResult intent, Long projectId) {
        // Extract citations from graph results
        List<QAResponse.Citation> citations = new ArrayList<>();
        // This would be populated based on actual query results
        return citations;
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
}