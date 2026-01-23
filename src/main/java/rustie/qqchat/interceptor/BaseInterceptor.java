package rustie.qqchat.interceptor;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotMessageEventInterceptor;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.utils.IdHolder;

@Component
@Slf4j
public class BaseInterceptor implements  BotMessageEventInterceptor {

    @Override
    public boolean preHandle(Bot bot, MessageEvent event){
        if(event instanceof GroupMessageEvent groupEvent){
            IdHolder.setGroupId(groupEvent.getGroupId());
            log.info("Set Group ID: {}", groupEvent.getGroupId());
            return true;
        }else if(event instanceof PrivateMessageEvent){
            IdHolder.setPrivateId(event.getUserId());
            log.info("Set Private ID: {}", event.getUserId());
            return true;
        }
        return false;
    }

    @Override
    public void afterCompletion(Bot bot, MessageEvent event)  {
        log.info("Clearing IDs from IdHolder");
        IdHolder.removeAll();
    }
}
