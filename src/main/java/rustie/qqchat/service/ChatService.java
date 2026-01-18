package rustie.qqchat.service;

import org.springframework.stereotype.Service;
import rustie.qqchat.client.LLMClient;
import rustie.qqchat.entity.ChatTextSearchResult;
import tools.jackson.databind.ObjectMapper;


import java.util.List;
@Service
public class ChatService {
    private final LLMClient llmClient;
    private final HybridSearchService searchService;
    private final ObjectMapper objectMapper;
    private final MessageHistoryService messageHistoryService;

    public ChatService(LLMClient llmClient,
                       HybridSearchService searchService,
                       ObjectMapper objectMapper,
                       MessageHistoryService messageHistoryService) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.searchService = searchService;
        this.messageHistoryService = messageHistoryService;
    }

    public String normalChatPrivate(long userId, String userMessage) {
        List<java.util.Map<String, String>> history = messageHistoryService.getPrivateHistory(userId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.savePrivateTurn(userId, userMessage, response);
        return response;
    }

    public String normalChatGroup(long groupId, long groupMemberId, String userMessage) {
        List<java.util.Map<String, String>> history = messageHistoryService.getGroupHistory(groupId);
        String response = llmClient.normalResponse(userMessage, "", history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, response);
        return response;
    }

    public String ragChatPrivate(long userId, String userMessage) {
        return ragChatPrivate(userId, userMessage, 5);
    }

    public String ragChatPrivate(long userId, String userMessage, int topK) {
        List<ChatTextSearchResult> searchResults = searchService.search(userMessage, topK);
        String context = buildContext(searchResults);
        List<java.util.Map<String, String>> history = messageHistoryService.getPrivateHistory(userId);
        String response = llmClient.normalResponse(userMessage, context, history);
        messageHistoryService.savePrivateTurn(userId, userMessage, response);
        return response;
    }

    public String ragChatGroup(long groupId, long groupMemberId, String userMessage) {
        return ragChatGroup(groupId, groupMemberId, userMessage, 5);
    }

    public String ragChatGroup(long groupId, long groupMemberId, String userMessage, int topK) {
        List<ChatTextSearchResult> searchResults = searchService.search(userMessage, topK);
        String context = buildContext(searchResults);
        List<java.util.Map<String, String>> history = messageHistoryService.getGroupHistory(groupId);
        String response = llmClient.normalResponse(userMessage, context, history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, response);
        return response;
    }
    // 新增: 带解释的 RAG 聊天。返回检索过程信息 + 最终回答。
    public String ragChatExplainGroup(long groupId, long groupMemberId, String userMessage) {
        final int topK = 5; // 与默认 ragChat 保持一致
        HybridSearchService.SearchDetail detail = searchService.searchDetail(userMessage, topK);
        List<ChatTextSearchResult> fused = detail.fused;
        StringBuilder explain = new StringBuilder();
        explain.append("=== RAG检索执行情况 ===\n");
        explain.append("查询: ").append(userMessage).append("\n");
        explain.append("TopK: ").append(topK).append(", recallK: ").append(detail.recallK).append(", 向量可用: ").append(detail.vectorEnabled).append("\n");
        // KNN 原始结果
        explain.append("-- KNN 原始结果 (按ES得分排序) --\n");
        appendResultList(explain, detail.knn, 10); // 只展示前10条避免过长
        // BM25 原始结果
        explain.append("-- BM25 原始结果 (按ES得分排序) --\n");
        appendResultList(explain, detail.bm25, 10);
        // 融合结果
        explain.append("-- RRF 融合后最终结果 --\n");
        appendResultList(explain, fused, fused.size());
        if (fused == null || fused.isEmpty()) {
            explain.append("未检索到相关上下文,将直接根据已有对话历史回答。\n\n");
        }
        String context = buildContext(fused);
        List<java.util.Map<String, String>> history = messageHistoryService.getGroupHistory(groupId);
        String answer = llmClient.normalResponse(userMessage, context, history);
        messageHistoryService.saveGroupTurn(groupId, groupMemberId, userMessage, answer);
        explain.append("=== 模型回答 ===\n");
        explain.append(answer);
        return explain.toString();
    }

    private void appendResultList(StringBuilder sb,List<ChatTextSearchResult> list,int limit){
        if (list == null || list.isEmpty()) {
            sb.append("(空)\n");
            return;
        }
        final int MAX_SNIPPET_LEN = 180;
        for (int i = 0; i < list.size() && i < limit; i++) {
            ChatTextSearchResult r = list.get(i);
            String snippet = r.getContent();
            if (snippet == null) snippet = "";
            if (snippet.length() > MAX_SNIPPET_LEN) snippet = snippet.substring(0, MAX_SNIPPET_LEN) + "…";
            String user = r.getUserName() != null ? r.getUserName() : (r.getUserId() != null ? r.getUserId() : "未知用户");
            String group = r.getGroupId() != null ? r.getGroupId() : "-";
            String scope = r.getScope() != null ? r.getScope() : "-";
            String timeStr = r.getCreateTime() != null ? java.time.Instant.ofEpochMilli(r.getCreateTime()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString() : "-";
            Double score = r.getScore();
            String scoreStr = score != null ? String.format("%.4f", score) : "-";
            sb.append(String.format("[%d] score=%s user=%s group=%s scope=%s time=%s\n%s\n", i + 1, scoreStr, user, group, scope, timeStr, snippet));
        }
        sb.append("\n");
    }
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
