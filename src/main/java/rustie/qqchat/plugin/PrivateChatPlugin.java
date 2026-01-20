package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.utils.ChatUtils;
import rustie.qqchat.service.ChatService;

import java.util.List;

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
        String rawMessage = ChatUtils.getEventPlainText(event);
        if(rawMessage.startsWith("/"))return;
        String message = rawMessage.replaceAll("\\s+", " ").trim();
        List<String> imageUrls = ChatUtils.getEventImageUrls(event);
        String response = chatService.normalChatPrivate(event.getUserId(), message, imageUrls);
        String sendMsg = MsgUtils.builder().text(response).build();
        bot.sendPrivateMsg(event.getUserId(), sendMsg, false);
    }

}