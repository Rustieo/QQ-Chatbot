package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.client.ModelType;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.service.ChatService;
import rustie.qqchat.service.SettingService;

@Component
@Slf4j
@Shiro
public class SettingPlugin {

    private final SettingService settingService;
    private final ChatService chatService;
    private final LLMClient llmClient;
    public SettingPlugin(SettingService settingService, ChatService chatService, LLMClient llmClient) {
        this.settingService = settingService;
        this.chatService = chatService;
        this.llmClient = llmClient;
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/sysprompt.*")
    public void setSystemRole(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateSystemPrompt(event.getMessage());
        // 系统角色变化会影响上下文，清空当前群会话的历史（DB + 缓存）
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/topp.*")
    public void updateTopP(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateTopP(event.getMessage());
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/temperature.*")
    public void updateTemperature(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateTemperature(event.getMessage());
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd="^/new$")
    public void newChat(Bot bot, GroupMessageEvent event) {
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), "嗯啊,复活了", false);
        settingService.updateSystemPrompt("你是一个喜欢跟人聊天的AI助手");
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd="^/clear$")
    public void clearChat(Bot bot, GroupMessageEvent event) {
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), "嗯啊,忘了", false);
    }

    // ===== 新增: 行为规则管理 =====
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/add.*")
    public void addLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.addLimit(event.getMessage());
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/del.*")
    public void delLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.deleteLimit(event.getMessage());
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/list$")
    public void listLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.listLimits();
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/clear$")
    public void clearLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.clearLimits();
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    // ===== 新增: 模型切换 =====
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/model.*")
    public void switchModel(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.switchModel(event.getMessage(), llmClient);
        chatService.clearGroupHistory(event.getGroupId());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/model/deepseek$")
    public void switchToDeepSeek(Bot bot, GroupMessageEvent event) {
        boolean ok = llmClient.switchModel(ModelType.DeepSeek);
        String msg = ok ? "已切换到 DeepSeek" : "切换 DeepSeek 失败";
        bot.sendGroupMsg(event.getGroupId(), msg, false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/model/qwen$")
    public void switchToQwen(Bot bot, GroupMessageEvent event) {
        boolean ok = llmClient.switchModel(ModelType.Qwen);
        String msg = ok ? "已切换到 Qwen" : "切换 Qwen 失败";
        bot.sendGroupMsg(event.getGroupId(), msg, false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd="^/help$")
    public void showHelp(Bot bot, AnyMessageEvent event) {
        bot.sendMsg(event, settingService.buildHelpMessage(), false);
    }
}