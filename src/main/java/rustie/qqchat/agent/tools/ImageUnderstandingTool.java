package rustie.qqchat.agent.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rustie.qqchat.agent.Tool;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.utils.IdHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Expose Qwen-VL image understanding as a tool for DeepSeek to call.
 * NOTE: tool calls are not persisted to message history.
 */
@Component
@RequiredArgsConstructor
public class ImageUnderstandingTool implements Tool {
    private final LLMClient llmClient;

    @Override
    public String name() {
        return "image_understanding";
    }

    @Override
    public String description() {
        return "图片理解工具：用于理解图片内容，并结合用户问题给出描述/答案。"
            + "可理解当前消息携带的图片，或传入 image_urls 指定要理解的图片URL。"
            + "当你需要基于图片内容生成/改图提示词但用户没有描述图片内容时，应先调用本工具，避免擅自猜测。";
    }

    @Override
    public ObjectNode parameters(ObjectMapper om) {
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = om.createObjectNode();
        ObjectNode q = om.createObjectNode();
        q.put("type", "string");
        q.put("description", "你想让工具回答的问题/指令（可为空，默认：描述图片内容）。");
        props.set("question", q);

        ObjectNode imageUrls = om.createObjectNode();
        imageUrls.put("type", "array");
        imageUrls.putObject("items").put("type", "string");
        imageUrls.put("description", "要理解的图片URL列表（可选；不传则使用当前消息携带的图片）。");
        props.set("image_urls", imageUrls);

        schema.set("properties", props);
        schema.set("required", om.createArrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode args, ObjectMapper om) throws Exception {
        String question = args != null && args.has("question") && args.get("question").isTextual()
                ? args.get("question").asText()
                : null;

        List<String> imageUrls = null;
        if (args != null && args.has("image_urls") && args.get("image_urls").isArray()) {
            java.util.ArrayList<String> tmp = new java.util.ArrayList<>();
            for (JsonNode n : args.get("image_urls")) {
                if (n != null && n.isTextual() && !n.asText("").isBlank()) tmp.add(n.asText());
            }
            if (!tmp.isEmpty()) imageUrls = tmp;
        }

        if (imageUrls == null) {
            imageUrls = IdHolder.getImageUrls();
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            return om.createObjectNode()
                    .put("error", "no_images")
                    .put("message", "No image urls in current context.");
        }

        String text = llmClient.imageUnderstanding(question, imageUrls);
        ObjectNode out = om.createObjectNode();
        out.put("text", text);
        out.put("image_count", imageUrls.size());
        return out;
    }
}

