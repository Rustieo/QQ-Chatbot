package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import rustie.qqchat.client.LLMClient;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.service.ChatService;
import rustie.qqchat.utils.ChatUtils;
import rustie.qqchat.utils.IdHolder;

import java.util.List;

@Shiro
@Component
@Slf4j
public class GroupChatPlugin {

    private final ChatService chatService;
    private final LLMClient llmClient;
    private final AiProperties aiProperties;


    public GroupChatPlugin(ChatService chatService, LLMClient llmClient, AiProperties aiProperties) {
        this.chatService = chatService;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }
    // 更多用法详见 @MessageHandlerFilter 注解源码

    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    public void normalChat(Bot bot, GroupMessageEvent event) {
        String rawMessage = ChatUtils.getEventPlainText(event);
        if(rawMessage.startsWith("/"))return;
        String text = rawMessage.replaceAll("\\s+", " ").trim();
        List<String> imageUrls = ChatUtils.getEventImageUrls(event);
        IdHolder.setImageUrls(imageUrls);
        String response = chatService.normalChatGroup(event.getGroupId(), event.getUserId(), text, imageUrls);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendGroupMsg(event.getGroupId(), sendMsg, false);
    }

//    @GroupMessageHandler
//    // 从 @ 提醒触发改为直接正则匹配 /rq 开头
//    @MessageHandlerFilter(cmd = "^/rq.*")
//    public void ragChat(Bot bot, GroupMessageEvent event) {
//        String message = ChatUtils.getEventPlainText(event).replaceAll("\\s+", " ").trim();
//        List<String> imageUrls = ChatUtils.getEventImageUrls(event);
//        String response = chatService.ragChatGroup(event.getGroupId(), event.getUserId(), message, imageUrls, 5);
//        String sendMsg = MsgUtils.builder().text(response).build();
//        bot.sendGroupMsg(event.getGroupId(), sendMsg, false);
//    }

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
        chatService.clearGroupHistory(event.getGroupId());
        try {
            // 生成系统提示词不应污染群聊历史，因此直接调用底层模型，不记录历史
            String prompt = llmClient.normalResponse(instruction, "", null);
            String trimmedPrompt = prompt.trim();
            aiProperties.getPrompt().setRoles(trimmedPrompt);
            llmClient.setSystemRole(trimmedPrompt);
            chatService.clearGroupHistory(event.getGroupId());
            bot.sendGroupMsg(event.getGroupId(), "已根据描述更新系统角色", false);
        } catch (Exception ex) {
            log.error("调用 DeepSeek 生成系统提示词失败", ex);
            bot.sendGroupMsg(event.getGroupId(), "生成系统提示词失败,请稍后再试", false);
        }
    }
}
