package rustie.qqchat.client;


import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import rustie.qqchat.agent.Tool;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.model.dto.ChatMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Getter
@Setter
public class LLMClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // WebClient 默认不会强制 10s 读取超时；OkHttp 默认 readTimeout=10s，会导致多模态/长耗时请求超时。
    // 这里保持与旧实现一致：不对读取/写入设置硬超时（仅保留连接超时）。
    private final OkHttpClient httpClient ;
    private String apiKey;
    private String model;
    private String baseUrl;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private String systemRole;
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);
    private final List<String> buffer = new ArrayList<>();
    private final Map<ModelType, ModelInfo> models = new HashMap<>();
    private ModelType currentModelType;
    private final String qwenVlModel;

    public LLMClient(@Value("${deepseek.api.url}") String deepSeekUrl,
                     @Value("${deepseek.api.key}") String deepSeekKey,
                     @Value("${deepseek.api.model}") String deepSeekModel,
                     @Value("${qwen.api.url}") String qwenUrl,
                     @Value("${qwen.api.key}") String qwenKey,
                     @Value("${qwen.api.model}") String qwenModel,
                     @Value("${qwen.api.vl-model:qwen3-vl-plus}") String qwenVlModel,
                     ObjectMapper objectMapper,
                     AiProperties aiProperties,
                     OkHttpClient httpClient) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.qwenVlModel = qwenVlModel;
        this.httpClient = httpClient;
        // 初始化模型信息表
        models.put(ModelType.DeepSeek, new ModelInfo(deepSeekUrl, deepSeekKey, deepSeekModel));
        models.put(ModelType.Qwen, new ModelInfo(qwenUrl, qwenKey, qwenModel));
        // 默认使用 DeepSeek
        switchModel(ModelType.DeepSeek);
    }

    public JsonNode createChatCompletion(ArrayNode messages, @Nullable ArrayNode tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        String raw;
        try {
            raw = postJson(this.baseUrl, this.apiKey, "/chat/completions", body);
        } catch (HttpStatusException e) {
            if (e.responseBody != null && !e.responseBody.isBlank()) {
                logger.error("调用模型 chat/completions 失败: status={} {}",
                        e.statusCode, e.statusText, e);
            } else {
                logger.error("调用模型 chat/completions 失败: status={} {} (empty body)",
                        e.statusCode, e.statusText, e);
            }
            throw new RuntimeException("调用模型 chat/completions 失败: " + e.statusCode, e);
        } catch (Exception e) {
            logger.error("调用模型 chat/completions 失败", e);
            throw new RuntimeException("调用模型 chat/completions 失败", e);
        }
        if (raw.isEmpty()) {
            throw new RuntimeException("模型返回空响应");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            logger.error("解析 chat/completions 响应失败", e);
            throw new RuntimeException("解析 chat/completions 响应失败", e);
        }
    }

    public JsonNode createChatCompletion(ArrayNode messages, @Nullable ArrayNode tools, @Nullable String traceId) {
        // traceId is currently only for upstream logging; keep signature for agent compatibility.
        return createChatCompletion(messages, tools);
    }

    public static ArrayNode toolsPayload(ObjectMapper om, List<Tool> tools) {
        ArrayNode arr = om.createArrayNode();
        for (var t : tools) {
            ObjectNode toolNode = om.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode fn = toolNode.putObject("function");
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.set("parameters", t.parameters(om));
            arr.add(toolNode);
        }
        return arr;
    }

    public synchronized boolean switchModel(ModelType type) {
        ModelInfo info = models.get(type);
        if (info == null) {
            logger.error("尝试切换到未配置的模型: {}", type);
            return false;
        }
        this.baseUrl = info.url;
        this.apiKey = info.apiKey;
        this.model = info.model;
        this.currentModelType = type;
        logger.info("已切换模型为: {} (model={})", type, this.model);
        return true;
    }

    public String normalResponse(String userMessage,
                                 String context,
                                 @Nullable List<ChatMessage> history) {
        Map<String, Object> request = buildRequest(userMessage, context, history, false);
        String raw;
        try {
            raw = postJson(this.baseUrl, this.apiKey, "/chat/completions", request);
        } catch (HttpStatusException e) {
            if (e.responseBody != null && !e.responseBody.isBlank()) {
                logger.error("调用模型接口失败: status={} {}",
                        e.statusCode, e.statusText, e);
            } else {
                logger.error("调用模型接口失败: status={} {} (empty body)",
                        e.statusCode, e.statusText, e);
            }
            throw new RuntimeException("调用模型接口失败: " + e.statusCode, e);
        } catch (Exception e) {
            logger.error("调用模型接口失败", e);
            throw new RuntimeException("调用模型接口失败", e);
        }
        if (raw.isEmpty()) {
            logger.error("模型返回空响应");
            throw new RuntimeException("模型返回空响应");
        }
        return extractFullContent(raw);
    }

    private Map<String, Object> buildRequest(String userMessage,
                                             String context,
                                             @Nullable List<ChatMessage> history,
                                             boolean stream) {
        logger.info("构建请求，用户消息：{}，上下文长度：{}，历史消息数：{}",
                userMessage,
                context != null ? context.length() : 0,
                history != null ? history.size() : 0);

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", model);
        request.put("messages", buildMessages(userMessage, context, history));
        request.put("stream", stream);
        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null)   request.put("temperature", gen.getTemperature());
        if (gen.getTopP() != null)          request.put("top_p", gen.getTopP());
        if (gen.getMaxTokens() != null)     request.put("max_tokens", gen.getMaxTokens());
        return request;
    }

    private List<Map<String, Object>> buildMessages(String userMessage,
                                                    String context,
                                                    @Nullable List<ChatMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        AiProperties.Prompt promptCfg = aiProperties.getPrompt();

        messages.add(Map.of("role", "system", "content", buildSystemPrompt(promptCfg)));

        // =================================================================
        // 2. History 区域：历史对话 (History)
        // =================================================================
        if (history != null && !history.isEmpty()) {
            for (ChatMessage h : history) {
                if (h == null) continue;
                messages.add(Map.of("role", h.role(), "content", h.content()));
            }
        }

        // =================================================================
        // 3. User 区域:当前问题 (Context + Question)
        // =================================================================
        StringBuilder userContentBuilder = new StringBuilder();
        userContentBuilder.append("我的问题是：").append(userMessage);

        messages.add(Map.of("role", "user", "content", userContentBuilder.toString()));
        logger.debug("构建消息完成: System规则长度={}, History条数={}, 最终User内容长度={}",
                messages.getFirst().get("content") != null ? String.valueOf(messages.getFirst().get("content")).length() : 0,
                (history != null ? history.size() : 0),
                userContentBuilder.length());

        log.debug("\n\n构建消息完成: {}", messages);
        return messages;
    }

    /**
     * 图片理解（多模态）。
     * 注意：不参与记忆/持久化；调用方应自行确保不写入 history / DB。
     */
    public String imageUnderstanding(@Nullable String userText, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return normalResponse(userText == null ? "" : userText, "", null);
        }

        ModelInfo info = models.get(ModelType.Qwen);
        if (info == null) {
            throw new IllegalStateException("Qwen model is not configured.");
        }
        String prompt = (userText == null || userText.isBlank()) ? "请描述图片内容，并回答我可能想问的问题（如有）。" : userText.trim();

        List<Map<String, Object>> content = new ArrayList<>(imageUrls.size() + 1);
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) continue;
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", url)
            ));
        }
        content.add(Map.of("type", "text", "text", prompt));

        List<Map<String, Object>> messages = new ArrayList<>(2);
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(aiProperties.getPrompt())));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> request = new HashMap<>();
        request.put("model", qwenVlModel);
        request.put("messages", messages);
        request.put("stream", false);

        // generation params (reuse)
        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null) request.put("temperature", gen.getTemperature());
        if (gen.getTopP() != null) request.put("top_p", gen.getTopP());
        if (gen.getMaxTokens() != null) request.put("max_tokens", gen.getMaxTokens());

        String raw;
        try {
            raw = postJson(info.url, info.apiKey, "/chat/completions", request);
        } catch (HttpStatusException e) {
            if (e.responseBody != null && !e.responseBody.isBlank()) {
                logger.error("调用千问多模态接口失败: status={} {}",
                        e.statusCode, e.statusText, e);
            } else {
                logger.error("调用千问多模态接口失败: status={} {} (empty body)",
                        e.statusCode, e.statusText, e);
            }
            throw new RuntimeException("调用千问多模态接口失败: " + e.statusCode, e);
        } catch (Exception e) {
            logger.error("调用千问多模态接口失败", e);
            throw new RuntimeException("调用千问多模态接口失败", e);
        }

        if (raw.isEmpty()) {
            throw new RuntimeException("千问多模态返回空响应");
        }
        return extractFullContent(raw);
    }


    private String postJson(String baseUrl, @Nullable String apiKey, String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        Request.Builder rb = new Request.Builder()
                .url(joinUrl(baseUrl, path))
                .post(RequestBody.create(json, JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            rb.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        try (Response resp = httpClient.newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new HttpStatusException(resp.code(), resp.message(), respBody);
            }
            return respBody;
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl == null) baseUrl = "";
        if (path == null) path = "";
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private static String buildSystemPrompt(AiProperties.Prompt promptCfg) {
        // 仅保留 "人设" 和 "核心规则" (Role + Limitations)
        StringBuilder systemBuilder = new StringBuilder();
        String roles = promptCfg != null ? promptCfg.getRoles() : null;
        if (roles != null && !roles.isBlank()) {
            systemBuilder.append(roles);
        } else {
            systemBuilder.append("你是一个专业的AI助手。");
        }

        List<String> limits = promptCfg != null ? promptCfg.getLimits() : null;
        if (limits != null && !limits.isEmpty()) {
            systemBuilder.append("\n\n【行为准则】\n");
            for (int i = 0; i < limits.size(); i++) {
                systemBuilder.append(i + 1).append(". ").append(limits.get(i)).append("\n");
            }
        }
        return systemBuilder.toString();
    }

    /**
     * 解析非流式完整响应：choices[0].message.content
     */
    private String extractFullContent(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);

            // 先检查是否有 error
            JsonNode errorNode = node.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                JsonNode msgNode = errorNode.path("message");
                String msg;
                if (msgNode.isMissingNode() || msgNode.isNull()) msg = "模型返回错误";
                else if (msgNode.isString()) msg = msgNode.asString();
                else msg = msgNode.toString();
                logger.error("模型错误响应: {}", msg);
                throw new RuntimeException(msg);
            }

            JsonNode contentNode = node.path("choices")
                    .path(0)
                    .path("message")
                    .path("content");
            String content;
            if (contentNode.isMissingNode() || contentNode.isNull()) content = "";
            else if (contentNode.isString()) content = contentNode.asString();
            else content = contentNode.toString();

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

    private static final class HttpStatusException extends RuntimeException {
        final int statusCode;
        final String statusText;
        final String responseBody;

        HttpStatusException(int statusCode, String statusText, String responseBody) {
            super("HTTP " + statusCode + " " + statusText);
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.responseBody = responseBody;
        }
    }

}