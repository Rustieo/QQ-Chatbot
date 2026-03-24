package rustie.qqchat.agent.tools;

import com.mikuac.shiro.common.utils.ShiroUtils;
import org.springframework.stereotype.Component;
import rustie.qqchat.agent.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tool: get QQ user avatar URL for further reasoning.
 * Unlike {@link UserAvatarTool}, this tool does NOT returnDirect,
 * so the LLM can use the URL as input for other tools (e.g. gen_image).
 */
@Component
public class UserAvatarUrlTool implements Tool {

    @Override
    public String name() {
        return "get_user_avatar_url";
    }

    @Override
    public String description() {
        return "获取指定 QQ 号的头像链接（用于给其他工具当输入，不会直接结束对话）。"
                + "参数：user_id（必填，QQ号）；size（可选，0/40/100/640，默认640；0表示原图）。"
                + "当你需要‘拿到头像URL再去做生图/改图/风格化’时，优先调用本工具。";
    }

    @Override
    public ObjectNode parameters(ObjectMapper om) {
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = om.createObjectNode();

        ObjectNode userId = om.createObjectNode();
        userId.put("type", "integer");
        userId.put("description", "QQ号（必填）。");
        props.set("user_id", userId);

        ObjectNode size = om.createObjectNode();
        size.put("type", "integer");
        size.put("description", "头像尺寸：0(原图)/40/100/640，默认640。");
        props.set("size", size);

        schema.set("properties", props);
        ArrayNode req = om.createArrayNode();
        req.add("user_id");
        schema.set("required", req);
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode args, ObjectMapper om) {
        long userId = args != null && args.has("user_id") ? args.get("user_id").asLong(0) : 0;
        int size = args != null && args.has("size") ? args.get("size").asInt(640) : 640;
        if (userId <= 0) {
            return om.createObjectNode().put("error", "bad_args").put("message", "user_id is required");
        }

        String url = ShiroUtils.getUserAvatar(userId, size);
        ObjectNode out = om.createObjectNode();
        ArrayNode urls = om.createArrayNode();
        urls.add(url);
        out.put("user_id", userId);
        out.put("size", size);
        out.put("url", url);
        out.set("urls", urls);
        return out;
    }
}
