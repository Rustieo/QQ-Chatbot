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
import com.mikuac.shiro.enums.AtEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.client.DeepSeekClient;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.service.ChatService;

import java.util.regex.Matcher;

@Shiro
@Component
@Slf4j
public class SettingPlugin {

    private final AiProperties aiProperties;

    public SettingPlugin(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED,startWith = "/ds/role ")
    public void setSystemRole(Bot bot, GroupMessageEvent event) {
        String systemRole = event.getMessage().replaceFirst("/ds/role ", "").trim();
        AiProperties.Prompt prompt = aiProperties.getPrompt();
        prompt.setRules(systemRole);
        bot.sendGroupMsg(event.getGroupId(), "系统角色已更新",false);
    }

}