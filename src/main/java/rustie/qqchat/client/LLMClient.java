package rustie.qqchat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.Data;
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
public class LLMClient {

    // 可切换的运行时字段（去掉 final 以便动态更换模型）
    private WebClient webClient;
    private String apiKey;
    private String model;
    private final AiProperties aiProperties;
    private String systemRole;
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);
    private final List<String> buffer = new ArrayList<>();
    private final Map<ModelType, ModelInfo> models = new HashMap<>();
    private ModelType currentModelType;

    /**
     * 单构造：初始化所有可用模型配置，并默认启用 DeepSeek
     */
    public LLMClient(@Value("${deepseek.api.url}") String deepSeekUrl,
                     @Value("${deepseek.api.key}") String deepSeekKey,
                     @Value("${deepseek.api.model}") String deepSeekModel,
                     @Value("${qwen.api.url}") String qwenUrl,
                     @Value("${qwen.api.key}") String qwenKey,
                     @Value("${qwen.api.model}") String qwenModel,
                     AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        // 初始化模型信息表
        models.put(ModelType.DeepSeek, new ModelInfo(deepSeekUrl, deepSeekKey, deepSeekModel));
        models.put(ModelType.Qwen, new ModelInfo(qwenUrl, qwenKey, qwenModel));
        // 默认使用 DeepSeek
        switchModel(ModelType.DeepSeek);
    }

    /**
     * 切换当前使用的模型。如果模型配置不存在则返回 false。
     */
    public synchronized boolean switchModel(ModelType type) {
        ModelInfo info = models.get(type);
        if (info == null) {
            logger.error("尝试切换到未配置的模型: {}", type);
            return false;
        }
        WebClient.Builder builder = WebClient.builder().baseUrl(info.url);
        if (info.apiKey != null && !info.apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + info.apiKey);
        }
        this.webClient = builder.build();
        this.apiKey = info.apiKey;
        this.model = info.model;
        this.currentModelType = type;
        logger.info("已切换模型为: {} (model={})", type, this.model);
        return true;
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
            logger.error("调用模型接口失败", e);
            throw new RuntimeException("调用模型接口失败", e);
        }
        if (raw == null || raw.isEmpty()) {
            logger.error("模型返回空响应");
            throw new RuntimeException("模型返回空响应");
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
        // =================================================================
        // 1. System 区域：仅保留 "人设" 和 "核心规则" (Role + Limitations)
        // =================================================================
        StringBuilder systemBuilder = new StringBuilder();
        String roles = promptCfg.getRoles();

        // 默认兜底的人设，防止 rules 为空时由模型只有空 system
        if (roles != null && !roles.isBlank()) {
            systemBuilder.append(roles);
        } else {
            systemBuilder.append("你是一个专业的AI助手。请基于用户提供的上下文回答问题。");
        }

        ////聊天规则
        List<String> limits = promptCfg.getLimits();
        if (limits != null && !limits.isEmpty()) {
            systemBuilder.append("\n\n【行为准则】\n");
            for (int i = 0; i < limits.size(); i++) {
                int idx = i + 1;
                systemBuilder.append(idx).append(". ").append(limits.get(i)).append("\n");
            }

        }

        messages.add(Map.of(
                "role", "system",
                "content", systemBuilder.toString()
        ));

        // =================================================================
        // 2. History 区域：历史对话 (History)
        // =================================================================
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // =================================================================
        // 3. User 区域：RAG 上下文 + 当前问题 (Context + Question)
        // =================================================================
        StringBuilder userContentBuilder = new StringBuilder();

        // A. 注入 RAG 知识 (如果有)
        if (context != null && !context.isBlank()) {
            userContentBuilder.append("请参考以下 <context> 标签内的信息来回答我的问题：\n\n");
            userContentBuilder.append("<context>\n");
            userContentBuilder.append(context).append("\n");
            userContentBuilder.append("</context>\n\n");
            //这里可以降低幻觉
            userContentBuilder.append("（如果参考信息中没有答案，请直接说明，不要编造。）\n\n");
        } else {
            userContentBuilder.append("（暂无参考资料，请根据你的通用知识回答）\n\n");
        }
        // B. 注入当前用户问题
        userContentBuilder.append("我的问题是：").append(userMessage);

        messages.add(Map.of(
                "role", "user",
                "content", userContentBuilder.toString()
        ));
        logger.debug("构建消息完成: System规则长度={}, History条数={}, 最终User内容长度={}",
                systemBuilder.length(),
                (history != null ? history.size() : 0),
                userContentBuilder.length());

        log.debug("\n\n构建消息完成: {}", messages);
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
                String msg = errorNode.path("message").asText("模型返回错误");
                logger.error("模型错误响应: {}", msg);
                throw new RuntimeException(msg);
            }

            String content = node.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");

            if (content.isEmpty()) {
                logger.error("响应中没有 content 字段，原始响应: {}", response);
                throw new RuntimeException("响应中没有 content 字段");
            }

            return content;
        } catch (Exception e) {
            logger.error("解析模型完整响应失败", e);
            throw new RuntimeException("解析模型响应失败", e);
        }
    }

    @Data
    private static class ModelInfo {
        String url;
        String apiKey;
        String model;
        ModelInfo(String url, String apiKey, String model) {
            this.url = url;
            this.apiKey = apiKey;
            this.model = model;
        }
    }

}