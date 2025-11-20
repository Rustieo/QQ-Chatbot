package rustie.qqchat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import rustie.qqchat.client.DeepSeekClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
@Service
public class ChatService {
    List<Map<String, String>> history;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    //private final ConcurrentMap<Long, String> groupSystemPrompts = new ConcurrentHashMap<>();

    public ChatService(DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
        this.history = new ArrayList<>();
    }
    public  String handleUserMessage(String userMessage) {
            // 调用 DeepSeekClient 获取回复
            String response = deepSeekClient.normalResponse(userMessage, "", history);
            updateConversationHistory(userMessage, response);
            return response;
    }

//    public String handleGroupMessage(long groupId, String userMessage) {
//        return handleMessage(userMessage, groupSystemPrompts.get(groupId));
//    }



    private void updateConversationHistory(String userMessage, String response) {
        // 获取当前时间戳
        String currentTimestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        // 添加用户消息（带时间戳）
        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", userMessage);
        userMsgMap.put("timestamp", currentTimestamp);
        history.add(userMsgMap);

        // 添加助手回复（带时间戳）
        Map<String, String> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        assistantMsgMap.put("content", response);
        assistantMsgMap.put("timestamp", currentTimestamp);
        history.add(assistantMsgMap);

        // 限制历史记录长度，保留最近的20条消息
        if (history.size() > 20) {
            history = history.subList(history.size() - 20, history.size());
        }
    }
    public void clearHistory() {
        history.clear();
    }

}
