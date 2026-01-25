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
        return "图片理解工具：用于理解当前消息携带的图片内容，并结合用户问题给出描述/答案。"
                + "当用户消息包含图片且你需要知道图片内容时调用。";
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

        List<String> imageUrls = IdHolder.getImageUrls();
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

