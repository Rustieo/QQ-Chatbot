package rustie.qqchat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import rustie.qqchat.config.AiProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Getter
@Setter
public class DeepSeekClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final AiProperties aiProperties;
    private String systemRole;
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);
    private final List<String> buffer = new ArrayList<>();

    public DeepSeekClient(@Value("${deepseek.api.url}") String apiUrl,
                          @Value("${deepseek.api.key}") String apiKey,
                          @Value("${deepseek.api.model}") String model,
                          AiProperties aiProperties) {
        WebClient.Builder builder = WebClient.builder().baseUrl(apiUrl);

        // 只有当 API key 不为空时才添加 Authorization header
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        this.webClient = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.aiProperties = aiProperties;
    }

    /**
     * 非流式，一次性返回完整结果，适合 QQ 机器人回复
     */
    public String normalResponse(String userMessage,
                                 String context,
                                 @Nullable List<Map<String, String>> history) {
        Map<String, Object> request = buildRequest(userMessage, context, history, false);
        String raw;
        try {
            raw = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 阻塞拿到完整结果
        } catch (Exception e) {
            logger.error("调用 DeepSeek 接口失败", e);
            throw new RuntimeException("调用 DeepSeek 接口失败", e);
        }
        if (raw == null || raw.isEmpty()) {
            logger.error("DeepSeek 返回空响应");
            throw new RuntimeException("DeepSeek 返回空响应");
        }
        return extractFullContent(raw);
    }

    private Map<String, Object> buildRequest(String userMessage,
                                             String context,
                                             @Nullable List<Map<String, String>> history,
                                             boolean stream) {
        logger.info("构建请求，用户消息：{}，上下文长度：{}，历史消息数：{}",
                userMessage,
                context != null ? context.length() : 0,
                history != null ? history.size() : 0);

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", model);
        request.put("messages", buildMessages(userMessage, context, history));
        request.put("stream", stream);
        // 生成参数
        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null) {
            request.put("temperature", gen.getTemperature());
        }
        if (gen.getTopP() != null) {
            request.put("top_p", gen.getTopP());
        }
        if (gen.getMaxTokens() != null) {
            request.put("max_tokens", gen.getMaxTokens());
        }
        return request;
    }

    private List<Map<String, String>> buildMessages(String userMessage,
                                                    String context,
                                                    List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        AiProperties.Prompt promptCfg = aiProperties.getPrompt();

        // 1. 构建统一的 system 指令（规则 + 参考信息）
        StringBuilder sysBuilder = new StringBuilder();
        String rules=promptCfg.getRules();
        if (rules != null) {
            sysBuilder.append(rules).append("\n\n");
        }

        String refStart = promptCfg.getRefStart() != null ? promptCfg.getRefStart() : "<<REF>>";
        String refEnd = promptCfg.getRefEnd() != null ? promptCfg.getRefEnd() : "<<END>>";
        sysBuilder.append(refStart).append("\n");

        if (context != null && !context.isEmpty()) {
            sysBuilder.append(context);
        } else {
            String noResult = promptCfg.getNoResultText() != null ? promptCfg.getNoResultText() : "（本轮无检索结果）";
            sysBuilder.append(noResult).append("\n");
        }

        sysBuilder.append(refEnd);

        String systemContent = sysBuilder.toString();
        messages.add(Map.of(
                "role", "system",
                "content", systemContent
        ));
        logger.debug("添加了系统消息，长度: {}", systemContent.length());

        // 2. 追加历史消息（若有）
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // 3. 当前用户问题
        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        return messages;
    }

    /**
     * 解析非流式完整响应：choices[0].message.content
     */
    private String extractFullContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response);

            // 先检查是否有 error
            JsonNode errorNode = node.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                String msg = errorNode.path("message").asText("DeepSeek 返回错误");
                logger.error("DeepSeek 错误响应: {}", msg);
                throw new RuntimeException(msg);
            }

            String content = node.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");

            if (content.isEmpty()) {
                logger.error("DeepSeek 响应中没有 content 字段，原始响应: {}", response);
                throw new RuntimeException("DeepSeek 响应中没有 content 字段");
            }

            return content;
        } catch (Exception e) {
            logger.error("解析 DeepSeek 完整响应失败", e);
            throw new RuntimeException("解析 DeepSeek 响应失败", e);
        }
    }


}