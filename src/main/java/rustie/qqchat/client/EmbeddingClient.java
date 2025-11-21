package rustie.qqchat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// 嵌入向量生成客户端
@Component
public class EmbeddingClient {

    @Value("${embedding.api.model}")
    private String modelId;
    
    @Value("${embedding.api.batch-size:100}")
    private int batchSize;

    @Value("${embedding.api.dimension:2048}")
    private int dimension;
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(WebClient embeddingWebClient, ObjectMapper objectMapper) {
        this.webClient = embeddingWebClient;
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
        return webClient.post()
                .uri("/embeddings")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                        .filter(e -> e instanceof WebClientResponseException))
                .block(Duration.ofSeconds(30));
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
