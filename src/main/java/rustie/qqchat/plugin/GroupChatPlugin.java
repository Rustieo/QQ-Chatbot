package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.client.DeepSeekClient;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.service.ChatService;

import java.util.regex.Matcher;

@Shiro
@Component
@Slf4j
public class GroupChatPlugin {

    private final ChatService chatService;
    private final DeepSeekClient deepSeekClient;
    private final AiProperties aiProperties;

    public GroupChatPlugin(ChatService chatService, DeepSeekClient deepSeekClient, AiProperties aiProperties) {
        this.chatService = chatService;
        this.deepSeekClient = deepSeekClient;
        this.aiProperties = aiProperties;
    }
    // 更多用法详见 @MessageHandlerFilter 注解源码


    @GroupMessageHandler
    @MessageHandlerFilter(startWith = "/chat")
    public void normalChat(Bot bot, GroupMessageEvent event) {
        String rawMessage = event.getMessage();
        String message=rawMessage.replaceAll("\\s+", " ").trim();
        String response = chatService.normalChat(message);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendGroupMsg(event.getGroupId(), sendMsg, false);
    }

    @GroupMessageHandler
    // 从 @ 提醒触发改为直接正则匹配 /rq 开头
    @MessageHandlerFilter(cmd = "^/rq.*")
    public void ragChat(Bot bot, GroupMessageEvent event) {
        String message=event.getMessage().replaceAll("\\s+", " ").trim();
        String response = chatService.ragChat(message);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendGroupMsg(event.getGroupId(), sendMsg, false);
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/rq/ex.*")
    public void explainRagChat(Bot bot, GroupMessageEvent event) {
        String message=event.getMessage().replaceAll("\\s+", " ").trim();
        String response = chatService.ragChatExplain(message);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendGroupMsg(event.getGroupId(), sendMsg, false);
    }
    @GroupMessageHandler
    // 从 @ 提醒触发改为直接正则匹配 /role 开头
    @MessageHandlerFilter(cmd = "^/role.*")
    public void changeRoleWithDS(Bot bot, GroupMessageEvent event) {
        String description = event.getMessage().replaceFirst("/role", "").trim();
        if (description.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请提供角色需求描述", false);
            return;
        }

        String instruction = "你是一名提示词工程师,请根据下面的角色描述生成一段系统提示词,用于聊天机器人设定。要求：\n" +
                "1. 用中文。\n" +
                "2. 直接给出最终系统提示词内容,不要包含解释或列表。\n" +
                "角色描述:" + description;
        chatService.clearHistory();
        try {
            String prompt = chatService.normalChat(instruction);
            String trimmedPrompt = prompt.trim();
            aiProperties.getPrompt().setRules(trimmedPrompt);
            deepSeekClient.setSystemRole(trimmedPrompt);
            chatService.clearHistory();
            bot.sendGroupMsg(event.getGroupId(), "已根据描述更新系统角色", false);
        } catch (Exception ex) {
            log.error("调用 DeepSeek 生成系统提示词失败", ex);
            bot.sendGroupMsg(event.getGroupId(), "生成系统提示词失败,请稍后再试", false);
        }
    }


}
