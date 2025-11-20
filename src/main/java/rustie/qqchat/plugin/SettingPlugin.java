package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.client.DeepSeekClient;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.service.ChatService;

import java.util.regex.Pattern;

@Shiro
@Component
@Slf4j
public class SettingPlugin {

    private final AiProperties aiProperties;
    private final ChatService chatService;
    private final DeepSeekClient deepSeekClient;

    public SettingPlugin(AiProperties aiProperties, ChatService chatService, DeepSeekClient deepSeekClient) {
        this.aiProperties = aiProperties;
        this.chatService = chatService;
        this.deepSeekClient = deepSeekClient;
    }

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED, startWith = "/setting/ds/sysprompt")
    public void setSystemRole(Bot bot, GroupMessageEvent event) {
        String systemRole = extractPayload(event.getMessage(), "/setting/ds/sysprompt");
        if (systemRole.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请在命令后附上系统角色内容", false);
            return;
        }
        aiProperties.getPrompt().setRules(systemRole);
        chatService.clearHistory();
        bot.sendGroupMsg(event.getGroupId(), "系统角色已更新", false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED, startWith = "/setting/ds/top_p")
    public void updateTopP(Bot bot, GroupMessageEvent event) {
        String payload = extractPayload(event.getMessage(), "/setting/ds/top_p");
        if (payload.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请提供 0-1 之间的 top_p 数值", false);
            return;
        }
        try {
            double value = Double.parseDouble(payload);
            if (value < 0 || value > 1) {
                bot.sendGroupMsg(event.getGroupId(), "top_p 必须在 0 到 1 之间", false);
                return;
            }
            aiProperties.getGeneration().setTopP(value);
            chatService.clearHistory();
            bot.sendGroupMsg(event.getGroupId(), String.format("top_p 已更新为 %.2f", value), false);
        } catch (NumberFormatException ex) {
            bot.sendGroupMsg(event.getGroupId(), "无法解析 top_p，请输入合法的小数", false);
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED, startWith = "/help")
    public void showHelp(Bot bot, AnyMessageEvent event) {
        String help = "可用设置指令:\n" +
                "/setting/ds/sysprompt <文本>  更新系统角色提示词\n\n" +
                "/setting/ds/top_p <0-1>   调整 top_p\n\n" +
                "/role <描述>             让 DeepSeek 生成系统提示词";
        bot.sendMsg(event, help, false);
    }

    private String extractPayload(String message, String command) {
        return message.replaceFirst("(?i)^" + Pattern.quote(command) + "\\s*", "").trim();
    }
}