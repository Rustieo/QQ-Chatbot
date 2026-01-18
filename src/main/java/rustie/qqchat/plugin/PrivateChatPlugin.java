package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.service.ChatService;

import java.util.regex.Matcher;

@Shiro
@Component
@Slf4j
public class PrivateChatPlugin {

    private final ChatService chatService;

    public PrivateChatPlugin(ChatService chatService) {
        this.chatService = chatService;

    }
    // 更多用法详见 @MessageHandlerFilter 注解源码

    @PrivateMessageHandler
    public void callDeepSeekNormal(Bot bot, PrivateMessageEvent event) {
        String rawMessage = event.getMessage();
        String t=event.getRawMessage();
        log.info(rawMessage+"--------------------");
        log.info(t+"--------------------");
        if(rawMessage.startsWith("/"))return;
        String message=rawMessage.replaceAll("\\s+", " ").trim();
        String response = chatService.normalChatPrivate(event.getUserId(), message);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendPrivateMsg(event.getUserId(), sendMsg, false);
    }
    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "^/rq.*")
    public void callDeepSeekRag(Bot bot, PrivateMessageEvent event) {
        String message=event.getMessage().replaceAll("\\s+", " ").trim();
        String response = chatService.ragChatPrivate(event.getUserId(), message);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendPrivateMsg(event.getUserId(), sendMsg, false);
    }




}