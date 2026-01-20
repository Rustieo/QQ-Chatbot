package rustie.qqchat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rustie.qqchat.client.EmbeddingClient;
import rustie.qqchat.model.entity.ChatText;
import rustie.qqchat.model.entity.ChatTextDocument;

import java.util.UUID;

// 向量化服务类
@Service
public class VectorizationService {

    private static final Logger logger = LoggerFactory.getLogger(VectorizationService.class);

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchService elasticsearchService;

    /**
     * 执行向量化操作：将一条聊天消息向量化并写入 Elasticsearch
     */
    public void vectorize(ChatText chatText) {
        try {
            if (chatText == null || chatText.getContent() == null || chatText.getContent().isEmpty()) {
                logger.warn("向量化忽略空内容的 ChatText: {}", chatText);
                return;
            }

            String content = chatText.getContent();
            // 调用外部模型生成向量
            float[] vector = embeddingClient.embed(content);

            ChatTextDocument document = new ChatTextDocument();
            document.setId(UUID.randomUUID().toString());
            document.setContent(content);
            document.setVector(vector);
            document.setUserId(chatText.getUserId());
            document.setUserName(chatText.getUserName());
            document.setGroupId(chatText.getGroupId());
            document.setScope(chatText.getScope());
            document.setCreateTime(chatText.getCreateTime());

            // 写入 Elasticsearch
            elasticsearchService.addChatTextDocument(document);

            logger.info("向量化完成并已写入 ES, docId: {}", document.getId());
        } catch (Exception e) {
            logger.error("向量化失败", e);
            throw new RuntimeException("向量化失败", e);
        }
    }

}