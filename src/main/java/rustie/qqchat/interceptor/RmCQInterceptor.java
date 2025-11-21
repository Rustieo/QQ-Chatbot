package rustie.qqchat.interceptor;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotMessageEventInterceptor;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.exception.ShiroException;
import org.springframework.stereotype.Component;

//@Component
//public class RmCQInterceptor implements BotMessageEventInterceptor {
//
//    // 这里的 .*? 是核心：匹配任意字符，但尽可能少匹配，直到遇到 ]
//    private static final String CQ_CODE_REGEX = "\\[CQ:.*?]";
//
//    @Override
//    public boolean preHandle(Bot bot, MessageEvent event) {
//        String originalMessage = event.getMessage();
//
//        if (originalMessage == null) {
//            return true;
//        }
//
//        // 1. 移除 CQ 码
//        // 2. trim() 去除因为移除 CQ 码（特别是 at）可能留下的首尾空格
//        String cleanedMessage = originalMessage.replaceAll(CQ_CODE_REGEX, "").trim();
//
//        // --- 关键调试点 ---
//        // 建议打个断点或日志，确认 cleanedMessage 确实变了
//        // System.out.println("清洗前: " + originalMessage);
//        // System.out.println("清洗后: " + cleanedMessage);
//
//        // 更新消息
//        event.setMessage(cleanedMessage);
//
//        // 注意：Shiro 的 MessageEvent 通常有 message 和 rawMessage 两个字段
//        // 这是一个保险措施，有些地方可能会读 RawMessage
//        if (event instanceof GroupMessageEvent) {
//            ((GroupMessageEvent) event).setRawMessage(cleanedMessage);
//        } else if (event instanceof PrivateMessageEvent) {
//            ((PrivateMessageEvent) event).setRawMessage(cleanedMessage);
//        }
//
//        return true;
//    }
//
//    @Override
//    public void afterCompletion(Bot bot, MessageEvent event) throws ShiroException {
//    }
//}