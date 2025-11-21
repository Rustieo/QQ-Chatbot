package rustie.qqchat.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rustie.qqchat.entity.ChatText;
import rustie.qqchat.entity.ChatTextDocument;

import java.util.List;

// Elasticsearch操作封装服务
@Service
public class ElasticsearchService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);

    private static final String INDEX_NAME = "chat_knowledge_base";

    @Autowired
    private ElasticsearchClient esClient;


    public void bulkIndex(List<ChatTextDocument> documents) {
        // 简单实现：逐条写入，后续可优化为真正的 bulk API
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (ChatTextDocument doc : documents) {
            try {
                addChatTextDocument(doc);
            } catch (Exception e) {
                logger.error("批量写入文档失败, docId: {}", doc != null ? doc.getId() : null, e);
            }
        }
    }

    //把一条聊天记录加入Elasticsearch

    public void addChatTextDocument(ChatTextDocument document) {
        if (document == null) {
            return;
        }
        try {
            if (document.getId() == null || document.getId().isEmpty()) {
                document.setId(java.util.UUID.randomUUID().toString());
            }
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(document.getId())
                    .document(document)
            );
        } catch (Exception e) {
            logger.error("写入聊天记录到ES失败, docId: {}", document.getId(), e);
            throw new RuntimeException("写入聊天记录到ES失败", e);
        }
    }

    //NOTE:目前不打算实现这个,感觉基于聊天记录构建的知识库不好精确删除.后续可以按照userID或群id删除
    public void delete() {

    }
    public void clearChatTextDocumentIndex() {
        try {
            logger.info("开始清空聊天记录索引");

            // 创建DeleteByQueryRequest对象，删除knowledge_base索引中的所有文档
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(INDEX_NAME) // 指定索引名称
                    .query(q -> q
                            .matchAll(m -> m) // 匹配所有文档
                    )
            );

            // 执行删除操作
            esClient.deleteByQuery(request);

            logger.info("成功清空聊天记录索引");
        } catch (Exception e) {
            logger.error("清空聊天记录索引时发生错误: {}", e.getMessage());
            throw new RuntimeException("清空聊天记录索引失败", e);
        }
    }
}
