package rustie.qqchat.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rustie.qqchat.agent.Tool;
import rustie.qqchat.utils.IdHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Image generation / editing tool.
 * Important: returnDirect=true and includeResultInModelContext=false, so the tool output is not
 * sent back to the LLM. The caller should output this tool result directly to the user.
 */
@Component
@Slf4j
public class GenImageTool implements Tool {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GenImageTool(@Value("${genimg.api.url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                        @Value("${genimg.api.key:}") String apiKey,
                        @Value("${genimg.api.model:doubao-seedream-4-5-251128}")
                        String model,
                        OkHttpClient client) {
        this.client =client;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String name() {
        return "gen_image";
    }

    @Override
    public String description() {
        return "生图/改图工具：当用户倾向于生成图片（可无输入图）、改图、换装、风格转换、把图A改成图B等时调用。"
                + "该工具会直接把生成结果返回给用户（调用后无需再输出额外解释）。";
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public boolean includeResultInModelContext() {
        return false;
    }

    @Override
    public ObjectNode parameters(ObjectMapper om) {
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = om.createObjectNode();

        ObjectNode prompt = om.createObjectNode();
        prompt.put("type", "string");
        prompt.put("description", "生图/改图指令，例如：将图1的服装换为图2的服装。");
        props.set("prompt", prompt);

        ObjectNode size = om.createObjectNode();
        size.put("type", "string");
        size.put("description", "输出尺寸，例如：2K / 4K（可选，默认2K）。");
        props.set("size", size);

        ObjectNode watermark = om.createObjectNode();
        watermark.put("type", "boolean");
        watermark.put("description", "是否添加水印（可选，默认false）。");
        props.set("watermark", watermark);

        ObjectNode images = om.createObjectNode();
        images.put("type", "array");
        images.putObject("items").put("type", "string");
        images.put("description", "输入图片URL列表（可选；不传则使用当前消息携带的图片）。");
        props.set("image", images);

        schema.set("properties", props);
        ArrayNode req = om.createArrayNode();
        req.add("prompt");
        schema.set("required", req);
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode args, ObjectMapper om) throws Exception {
        String prompt = args != null && args.has("prompt") ? args.get("prompt").asString("") : "";
        if (prompt == null || prompt.isBlank()) {
            return om.createObjectNode().put("error", "bad_args").put("message", "prompt is required");
        }

        List<String> images = new ArrayList<>();
        if (args != null && args.has("image") && args.get("image").isArray()) {
            for (JsonNode n : args.get("image")) {
                if (n != null && n.isString() && !n.asString().isBlank()) images.add(n.asString());
            }
        }
        if (images.isEmpty()) {
            List<String> ctx = IdHolder.getImageUrls();
            if (ctx != null) images.addAll(ctx.stream().filter(s -> s != null && !s.isBlank()).toList());
        }

        String size = (args != null && args.has("size") && args.get("size").isString())
                ? args.get("size").asString()
                : "2K";
        boolean watermark = args != null && args.has("watermark") && args.get("watermark").isBoolean() && args.get("watermark").asBoolean();

        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.put("prompt", prompt);
        if (!images.isEmpty()) {
            ArrayNode arr = body.putArray("image");
            for (String url : images) arr.add(url);
        }
        body.put("sequential_image_generation", "disabled");
        body.put("size", size);
        body.put("watermark", watermark);

        String raw;
        Request request = new Request.Builder()
                .url(joinUrl(this.baseUrl, "/images/generations"))
                .post(RequestBody.create(om.writeValueAsString(body), JSON))
                .build();
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.newBuilder()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build();
        }

        try (Response resp = client.newCall(request).execute()) {
            raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.error("GenImage 调用失败: status={} {}", resp.code(), resp.message());
                return om.createObjectNode()
                        .put("error", "upstream_error")
                        .put("status", resp.code())
                        .put("message", raw);
            }
        }
        if (raw == null || raw.isBlank()) {
            return om.createObjectNode().put("error", "empty_response").put("message", "empty response");
        }

        JsonNode resp = om.readTree(raw);
        ArrayNode data = (ArrayNode) resp.path("data");
        ArrayNode urls = om.createArrayNode();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                JsonNode u = item.path("url");
                if (u != null && u.isString() && !u.asString().isBlank()) urls.add(u.asString());
            }
        }

        ObjectNode out = om.createObjectNode();
        out.put("model", resp.path("model").asString(model));
        out.set("urls", urls);
        if (resp.has("usage")) out.set("usage", resp.get("usage"));
        return out;
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl == null) baseUrl = "";
        if (path == null) path = "";
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    @Override
    public String toUserText(JsonNode result, ObjectMapper om) {
        if (result == null) return "";
        JsonNode urls = result.get("urls");
        if (urls != null && urls.isArray() && !urls.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode u : urls) {
                if (u != null && u.isString() && !u.asString().isBlank()) lines.add(u.asString());
            }
            if (!lines.isEmpty()) return String.join("\n", lines);
        }
        return om.writeValueAsString(result);
    }
}

