package rustie.qqchat.plugin;

import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rustie.qqchat.model.entity.ChatText;
import rustie.qqchat.service.ElasticsearchService;
import rustie.qqchat.service.HybridSearchService;
import rustie.qqchat.service.VectorizationService;

@Component
@Slf4j
@Shiro
public class RagPlugin {

    private final ElasticsearchService elasticsearchService;
    private final VectorizationService vectorizationService;
    private final HybridSearchService hybridSearchService;

    public RagPlugin(ElasticsearchService elasticsearchService,
                     VectorizationService vectorizationService,
                     HybridSearchService hybridSearchService) {
        this.elasticsearchService = elasticsearchService;
        this.vectorizationService = vectorizationService;
        this.hybridSearchService = hybridSearchService;
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/rag/add/common.*")
    public void addChatAsKnowledge(Bot bot, GroupMessageEvent event) {
        String raw = event.getMessage();
        if (raw == null) {
            bot.sendGroupMsg(event.getGroupId(), "消息内容为空，无法加入知识库", false);
            return;
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请输入要加入知识库的内容", false);
            return;
        }
        ChatText chatText = buildChatTextFromGroupNoMeta(event, text, "group");
        try {
            vectorizationService.vectorize(chatText);
            //TODO 这段逻辑最好优化下
            String preview = text.length() > 50 ? text.substring(0, 50) + "…" : text;
            String msg = MsgUtils.builder().text("了解です:" + preview).build();
            bot.sendGroupMsg(event.getGroupId(), msg, false);
            log.info("[RAG-ADD] groupId={}, userId={}, len={}", event.getGroupId(), event.getUserId(), text.length());
        } catch (Exception e) {
            log.error("向量化或写入知识库失败", e);
            bot.sendGroupMsg(event.getGroupId(), "写入知识库失败，请稍后重试", false);
        }
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/rag/add/meta.*")
    public void addChatAsKnowledgeWithName(Bot bot, GroupMessageEvent event) {
        String raw = event.getMessage();
        if (raw == null) {
            bot.sendGroupMsg(event.getGroupId(), "消息内容为空，无法加入知识库", false);
            return;
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请输入要加入知识库的内容", false);
            return;
        }
        ChatText chatText = buildChatTextFromGroup(event, text, "group");
        try {
            vectorizationService.vectorize(chatText);
            //TODO 这段逻辑最好优化下
            String preview = text.length() > 50 ? text.substring(0, 50) + "…" : text;
            String msg = MsgUtils.builder().text("了解です:" + preview).build();
            bot.sendGroupMsg(event.getGroupId(), msg, false);
            log.info("[RAG-ADD] groupId={}, userId={}, len={}", event.getGroupId(), event.getUserId(), text.length());
        } catch (Exception e) {
            log.error("向量化或写入知识库失败", e);
            bot.sendGroupMsg(event.getGroupId(), "写入知识库失败，请稍后重试", false);
        }
    }
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^/rag/rm.*")
    public void clearKnowledge(Bot bot, GroupMessageEvent event) {
        elasticsearchService.clearChatTextDocumentIndex();
    }
    private ChatText buildChatTextFromGroup(GroupMessageEvent event, String content, String scope) {
        ChatText chatText = new ChatText();
        chatText.setContent(content);
        chatText.setUserId(String.valueOf(event.getUserId()));
        String nickname = event.getSender() != null ? event.getSender().getNickname() : null;
        chatText.setUserName(nickname != null && !nickname.isEmpty() ? nickname : String.valueOf(event.getUserId()));
        chatText.setGroupId(String.valueOf(event.getGroupId()));
        chatText.setScope(scope);
        chatText.setCreateTime(System.currentTimeMillis());
        return chatText;
    }
    //构建chatText,但不加成员名
    private ChatText buildChatTextFromGroupNoMeta(GroupMessageEvent event, String content, String scope) {
        ChatText chatText = new ChatText();
        chatText.setContent(content);
        chatText.setGroupId(String.valueOf(event.getGroupId()));
        chatText.setScope(scope);
        chatText.setCreateTime(System.currentTimeMillis());
        return chatText;
    }


}
