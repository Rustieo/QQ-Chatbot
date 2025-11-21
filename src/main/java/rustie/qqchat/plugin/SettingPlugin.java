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
import rustie.qqchat.service.ChatService;
import rustie.qqchat.service.SettingService;

@Component
@Slf4j
@Shiro
public class SettingPlugin {

    private final SettingService settingService;
    private final ChatService chatService;
    public SettingPlugin(SettingService settingService, ChatService chatService) {
        this.settingService = settingService;
        this.chatService = chatService;
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/sysprompt.*")
    public void setSystemRole(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateSystemPrompt(event.getMessage());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/topp.*")
    public void updateTopP(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateTopP(event.getMessage());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/setting/ds/temperature.*")
    public void updateTemperature(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.updateTemperature(event.getMessage());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd="^/new$")
    public void newChat(Bot bot, GroupMessageEvent event) {
        chatService.clearHistory();
        bot.sendGroupMsg(event.getGroupId(), "嗯啊,复活了", false);
        settingService.updateSystemPrompt("你是一个喜欢跟人聊天的AI助手");
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd="^/clear$")
    public void clearChat(Bot bot, GroupMessageEvent event) {
        chatService.clearHistory();
        bot.sendGroupMsg(event.getGroupId(), "嗯啊,忘了", false);
    }

    // ===== 新增: 行为规则管理 =====
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/add.*")
    public void addLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.addLimit(event.getMessage());
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/set/ai/limit/del.*")
    public void delLimit(Bot bot, GroupMessageEvent event) {
        SettingService.CommandResult result = settingService.deleteLimit(event.getMessage());
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
        bot.sendGroupMsg(event.getGroupId(), result.message(), false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd="^/help$")
    public void showHelp(Bot bot, AnyMessageEvent event) {
        bot.sendMsg(event, settingService.buildHelpMessage(), false);
    }
}