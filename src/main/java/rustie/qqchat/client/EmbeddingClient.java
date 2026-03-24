package rustie.qqchat.client;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// 嵌入向量生成客户端
@Component
public class EmbeddingClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${embedding.api.url}")
    private String apiUrl;

    @Value("${embedding.api.key}")
    private String apiKey;

    @Value("${embedding.api.model}")
    private String modelId;
    
    @Value("${embedding.api.batch-size:100}")
    private int batchSize;

    @Value("${embedding.api.dimension:2048}")
    private int dimension;
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(ObjectMapper objectMapper) {
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用通义千问 API 生成向量
     * @return 对应的向量列表
     */
    public float[] embed(String text) {
        try {
            String response = callApiOnce(text);
            return parseVectors(response);
        } catch (Exception e) {
            logger.error("调用向量化 API 失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量生成失败", e);
        }
    }

    private String callApiOnce(String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", text);
        requestBody.put("dimension", dimension);  // 直接在根级别设置dimension
        requestBody.put("encoding_format", "float");  // 添加编码格式

        String json;
        try {
            json = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("构建 embeddings 请求失败", e);
        }

        String url = joinUrl(apiUrl, "/embeddings");
        // 保持与原先 Retry.fixedDelay(3, 1s) 一致：失败后重试 3 次（总共最多 4 次）
        for (int attempt = 0; attempt <= 3; attempt++) {
            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON));
            if (apiKey != null && !apiKey.isBlank()) {
                rb.header("Authorization", "Bearer " + apiKey);
            }

            try (Response resp = httpClient.newCall(rb.build()).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    return body;
                }
                if (attempt >= 3) {
                    throw new RuntimeException("向量化 API 返回错误: " + resp.code() + " " + resp.message()
                            + (body == null || body.isBlank() ? "" : (", body=" + body)));
                }
            } catch (Exception e) {
                if (attempt >= 3) throw new RuntimeException(e.getMessage(), e);
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("向量化 API 重试被中断", ie);
            }
        }
        throw new RuntimeException("向量化 API 调用失败");
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl == null) baseUrl = "";
        if (path == null) path = "";
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private float[] parseVectors(String response) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode data = jsonNode.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            logger.error("API 响应格式错误: data 字段不存在、不是数组或为空。响应: {}", response);
            throw new RuntimeException("API 响应格式错误: data 字段不存在、不是数组或为空");
        }
        JsonNode embeddingData = data.get(0);
        JsonNode embedding = embeddingData.get("embedding");

        if (embedding == null || !embedding.isArray()) {
            logger.error("API 响应格式错误: embedding 字段不存在或不是数组。响应: {}", response);
            throw new RuntimeException("API 响应格式错误: embedding 字段不存在或不是数组");
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }
}
