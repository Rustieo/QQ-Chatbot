package rustie.qqchat.agent.tools;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionList;
import com.mikuac.shiro.dto.action.response.GroupMemberInfoResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rustie.qqchat.agent.Tool;
import rustie.qqchat.utils.IdHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.annotation.concurrent.ThreadSafe;

@Component
@RequiredArgsConstructor
public class GroupMemberTool implements Tool {
    private final BotContainer botContainer;
    @Override
    public final String name() {
        return "get_group_member_list";
    }
    @Override
    public final String description() {
        return "获取当前群的成员列表（仅返回 userid(即QQ号)/nickname/sex）。"
            + "当用户只给了昵称/群名片（如：‘xxx’）但没给QQ号时，先调用本工具做匹配。"
            + "如果昵称命中多个候选，先把候选列出来并追问用户确认后再进行下一步。";
    }
    @Override
    public JsonNode execute(JsonNode args, ObjectMapper om) throws Exception {
        long groupId = IdHolder.getGroupId();
        if (groupId <= 0) return om.createObjectNode();
        //TODO 这里暂时硬编码
        Bot bot= botContainer.robots.get(3286864488L);
        ActionList<GroupMemberInfoResp> resp = bot.getGroupMemberList(groupId);
        ArrayNode out = om.createArrayNode();
        if (resp == null || resp.getData() == null) {
            return out;
        }
        for (GroupMemberInfoResp m : resp.getData()) {
            if (m == null) continue;
            ObjectNode row = om.createObjectNode();
            if (m.getUserId() != null) row.put("userid", m.getUserId());
            else row.putNull("userid");
            if (m.getNickname() != null) row.put("nickname", m.getNickname());
            else row.putNull("nickname");
            if (m.getSex() != null) row.put("sex", m.getSex());
            else row.putNull("sex");
            out.add(row);
        }
        return out;
    }
}
