package rustie.qqchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.model.dto.ChatMessage;
import rustie.qqchat.model.entity.ChatTextSearchResult;
import tools.jackson.databind.ObjectMapper;


import java.util.List;
@Service
@RequiredArgsConstructor
public class ChatService {
    private final LLMClient llmClient;
    //private final HybridSearchService searchService;
    private final ObjectMapper objectMapper;
    private final MessageHistoryService messageHistoryService;



    public String normalChatPrivate(long userId, String userMessage) {
        return normalChatPrivate(userId, userMessage, List.of());
    }

    /**
     * If imageUrls is not empty: use Qwen-VL for image understanding.
     * NOTE: image understanding is NOT persisted and NOT added to memory/history.
     */
    public String normalChatPrivate(long userId, String userMessage, List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            return llmClient.imageUnderstanding(userMessage, imageUrls);
        }
        List<ChatMessage> history = messageHistoryService.getPrivateHistory(userId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.savePrivateTurn(userId, userMessage, response);
        return response;
    }

    public String normalChatGroup(long groupId, long groupMemberId, String userMessage) {
        return normalChatGroup(groupId, groupMemberId, userMessage, List.of());
    }

    /**
     * If imageUrls is not empty: use Qwen-VL for image understanding.
     * NOTE: image understanding is NOT persisted and NOT added to memory/history.
     */
    public String normalChatGroup(long groupId, long groupMemberId, String userMessage, List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            return llmClient.imageUnderstanding(userMessage, imageUrls);
        }
        List<ChatMessage> history = messageHistoryService.getGroupHistory(groupId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, response);
        return response;
    }

//    public String ragChatPrivate(long userId, String userMessage) {
//        return ragChatPrivate(userId, userMessage, 5);
//    }

//    public String ragChatPrivate(long userId, String userMessage, int topK) {
//        return ragChatPrivate(userId, userMessage, List.of(), topK);
//    }

//    /**
//     * If imageUrls is not empty: use Qwen-VL for image understanding (no RAG/no memory/no persistence).
//     */
//    public String ragChatPrivate(long userId, String userMessage, List<String> imageUrls, int topK) {
//        if (imageUrls != null && !imageUrls.isEmpty()) {
//            return llmClient.imageUnderstanding(userMessage, imageUrls);
//        }
//        List<ChatTextSearchResult> searchResults = searchService.search(userMessage, topK);
//        String context = buildContext(searchResults);
//        List<ChatMessage> history = messageHistoryService.getPrivateHistory(userId);
//        String response = llmClient.normalResponse(userMessage, context, history);
//        messageHistoryService.savePrivateTurn(userId, userMessage, response);
//        return response;
//    }

//    public String ragChatGroup(long groupId, long groupMemberId, String userMessage) {
//        return ragChatGroup(groupId, groupMemberId, userMessage, 5);
//    }
//
//    public String ragChatGroup(long groupId, long groupMemberId, String userMessage, int topK) {
//        return ragChatGroup(groupId, groupMemberId, userMessage, List.of(), topK);
//    }

//    /**
//     * If imageUrls is not empty: use Qwen-VL for image understanding (no RAG/no memory/no persistence).
//     */
//    public String ragChatGroup(long groupId, long groupMemberId, String userMessage, List<String> imageUrls, int topK) {
//        if (imageUrls != null && !imageUrls.isEmpty()) {
//            return llmClient.imageUnderstanding(userMessage, imageUrls);
//        }
//        List<ChatTextSearchResult> searchResults = searchService.search(userMessage, topK);
//        String context = buildContext(searchResults);
//        List<ChatMessage> history = messageHistoryService.getGroupHistory(groupId);
//        String response = llmClient.normalResponse(userMessage, context, history);
//        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, response);
//        return response;
//    }


    private String buildContext(List<ChatTextSearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            // 返回空字符串，让 LLMClient 按"无检索结果"逻辑处理
            return "";
        }
        final int MAX_SNIPPET_LEN = 300; // 单段最长字符数，超出截断
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            ChatTextSearchResult result = searchResults.get(i);
            String snippet = result.getContent();
            if (snippet.length() > MAX_SNIPPET_LEN) {
                snippet = snippet.substring(0, MAX_SNIPPET_LEN) + "…";
            }
            //TODO:这里后续可以加上高人
            String userName= result.getUserName()!=null?result.getUserId():"未知用户";

            context.append(String.format("[%d] (%s),%s, %s\n", i + 1, userName,":", snippet));
        }
        return context.toString();
    }

    public void clearPrivateHistory(long userId) {
        messageHistoryService.clearPrivateHistory(userId);
    }

    public void clearGroupHistory(long groupId) {
        messageHistoryService.clearGroupHistory(groupId);
    }

}
