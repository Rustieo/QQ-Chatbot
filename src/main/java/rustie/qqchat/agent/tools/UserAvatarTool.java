package rustie.qqchat.agent.tools;

import com.mikuac.shiro.common.utils.ShiroUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import rustie.qqchat.agent.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tool: get QQ user avatar URL.
 * Returns direct so callers can render as images (MsgUtils.img).
 */
@Component
public class UserAvatarTool implements Tool {

    @Override
    public String name() {
        return "get_user_avatar";
    }
    @Bean
    TransactionAttributeSource transactionAttributeSource() {
        return new AnnotationTransactionAttributeSource(true);
    }
    @Override
    public String description() {
        return "获取指定 QQ 号的头像链接（用于直接发给用户看头像）。"
            + "参数：user_id（必填，QQ号）；size（可选，0/40/100/640，默认640；0表示原图）。"
            + "当用户要‘看某人的头像/发我头像/发xx头像’时调用。"
            + "注意：本工具 returnDirect=true（调用后会直接把链接作为最终回复返回，无法继续串联后续工具）。"
            + "如果你需要拿到头像URL后继续做生图/改图/风格化，请改用 get_user_avatar_url。";
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

    @Override
    public String toUserText(JsonNode result, ObjectMapper om) {
        if (result == null) return "";
        JsonNode url = result.get("url");
        return url != null && url.isTextual() ? url.asText("") : "";
    }
}
