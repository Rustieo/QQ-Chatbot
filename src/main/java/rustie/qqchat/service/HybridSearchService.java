package rustie.qqchat.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rustie.qqchat.client.EmbeddingClient;
import rustie.qqchat.entity.ChatTextDocument;
import rustie.qqchat.entity.ChatTextSearchResult;

import java.util.*;

/**
 * 混合搜索服务：结合文本匹配和向量相似度搜索（无权限过滤版本）
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    private static final int RRF_K = 60;

    private static final int RECALL_MULTIPLIER = 30;

    private static final String INDEX_NAME = "chat_knowledge_base";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    // 结构化详情结果用于解释
    public static class SearchDetail {
        public final List<ChatTextSearchResult> knn;
        public final List<ChatTextSearchResult> bm25;
        public final List<ChatTextSearchResult> fused;
        public final int recallK;
        public final boolean vectorEnabled;
        public SearchDetail(List<ChatTextSearchResult> knn,List<ChatTextSearchResult> bm25,List<ChatTextSearchResult> fused,int recallK,boolean vectorEnabled){
            this.knn=knn;this.bm25=bm25;this.fused=fused;this.recallK=recallK;this.vectorEnabled=vectorEnabled;
        }
    }

    //TODO 有时间完成带元数据的,不过估计很复杂,得上function calling.呃呃额饿啊额
    /**
     * 使用文本匹配和向量相似度进行混合搜索(不使用元数据)
     *
     * @param query 查询字符串
     * @param topK  返回结果数量
     */
    public List<ChatTextSearchResult> search(String query, int topK) {
        try {
            logger.debug("开始混合检索，query: {}, topK: {}", query, topK);

            final List<Float> queryVector = embedToVectorList(query);

            if (queryVector == null) {
                logger.warn("向量生成失败，仅使用文本匹配进行搜索");
                return textOnlySearch(query, topK);
            }

            int recallK = Math.min(topK * RECALL_MULTIPLIER, topK * 100);
            logger.debug("召回窗口 recallK = {} (topK = {})", recallK, topK);

            List<ChatTextSearchResult> knnResults = runKnnRecall(query, queryVector, recallK);
            List<ChatTextSearchResult> bm25Results = runBm25Recall(query, recallK);

            logger.debug("KNN 召回数量: {}, BM25 召回数量: {}", knnResults.size(), bm25Results.size());

            if (knnResults.isEmpty() && bm25Results.isEmpty()) {
                logger.info("KNN 与 BM25 均无召回结果");
                return Collections.emptyList();
            }

            Map<String, ChatTextSearchResult> docMap = new HashMap<>();
            List<String> knnDocIds = new ArrayList<>();
            List<String> bm25DocIds = new ArrayList<>();

            for (ChatTextSearchResult r : knnResults) {
                String docId = buildDocId(r);
                docMap.putIfAbsent(docId, r);
                knnDocIds.add(docId);
            }

            for (ChatTextSearchResult r : bm25Results) {
                String docId = buildDocId(r);
                docMap.putIfAbsent(docId, r);
                bm25DocIds.add(docId);
            }

            logger.debug("参与 RRF 融合的文档总数: {}", docMap.size());

            List<ChatTextSearchResult> fused = rrfMerge(knnDocIds, bm25DocIds, docMap, topK);
            logger.debug("RRF 融合后返回结果数量: {}", fused.size());
            return fused;
        } catch (Exception e) {
            logger.error("混合检索失败，使用纯文本搜索作为后备方案", e);
            try {
                return textOnlySearch(query, topK);
            } catch (Exception fallbackError) {
                logger.error("后备纯文本搜索也失败", fallbackError);
                return Collections.emptyList();
            }
        }
    }

    // 带详细信息的搜索
    public SearchDetail searchDetail(String query,int topK){
        try {
            final List<Float> queryVector = embedToVectorList(query);
            boolean vectorEnabled = queryVector!=null;
            if (!vectorEnabled){
                List<ChatTextSearchResult> textOnly = textOnlySearch(query, topK);
                return new SearchDetail(Collections.emptyList(),textOnly,textOnly,topK,false);
            }
            int recallK = Math.min(topK * RECALL_MULTIPLIER, topK * 100);
            List<ChatTextSearchResult> knnResults = runKnnRecall(query, queryVector, recallK);
            List<ChatTextSearchResult> bm25Results = runBm25Recall(query, recallK);
            if (knnResults.isEmpty() && bm25Results.isEmpty()) {
                return new SearchDetail(Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),recallK,true);
            }
            Map<String, ChatTextSearchResult> docMap = new HashMap<>();
            List<String> knnDocIds = new ArrayList<>();
            List<String> bm25DocIds = new ArrayList<>();
            for (ChatTextSearchResult r : knnResults) {
                String docId = buildDocId(r);
                docMap.putIfAbsent(docId, r);
                knnDocIds.add(docId);
            }
            for (ChatTextSearchResult r : bm25Results) {
                String docId = buildDocId(r);
                docMap.putIfAbsent(docId, r);
                bm25DocIds.add(docId);
            }
            List<ChatTextSearchResult> fused = rrfMerge(knnDocIds,bm25DocIds,docMap,topK);
            return new SearchDetail(knnResults,bm25Results,fused,recallK,true);
        }catch (Exception e){
            logger.error("searchDetail 失败",e);
            try {
                List<ChatTextSearchResult> textOnly = textOnlySearch(query, topK);
                return new SearchDetail(Collections.emptyList(),textOnly,textOnly,topK,false);
            } catch (Exception fallbackError) {
                return new SearchDetail(Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),topK,false);
            }
        }
    }

    private List<ChatTextSearchResult> runKnnRecall(String query, List<Float> queryVector, int recallK) {
        try {
            logger.debug("开始 KNN 召回，recallK = {}", recallK);

            SearchResponse<ChatTextDocument> response = esClient.search(s -> {
                        s.index(INDEX_NAME);
                        s.knn(kn -> kn
                                .field("vector")
                                .queryVector(queryVector)
                                .k(recallK)
                                .numCandidates(recallK)
                        );
                        s.query(q -> q.match(m -> m
                                .field("content")
                                .query(query)
                        ));
                        s.size(recallK);
                        return s;
                    }, ChatTextDocument.class);

            long total = response.hits().total() != null ? response.hits().total().value() : -1L;
            logger.debug("KNN 召回完成，ES 命中总数: {}, 实际返回: {}", total, response.hits().hits().size());

            List<ChatTextSearchResult> results = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                ChatTextDocument src = hit.source();
                if (src != null) {
                    ChatTextSearchResult r = new ChatTextSearchResult();
                    r.setId(src.getId());
                    r.setContent(src.getContent());
                    r.setScore(hit.score());
                    r.setUserId(src.getUserId());
                    r.setUserName(src.getUserName());
                    r.setGroupId(src.getGroupId());
                    r.setScope(src.getScope());
                    r.setCreateTime(src.getCreateTime());
                    results.add(r);
                }
            });
            return results;
        } catch (Exception e) {
            logger.error("KNN 召回失败", e);
            return Collections.emptyList();
        }
    }

    private List<ChatTextSearchResult> runBm25Recall(String query, int recallK) {
        try {
            logger.debug("开始 BM25 召回，recallK = {}", recallK);

            SearchResponse<ChatTextDocument> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q
                            .match(m -> m
                                    .field("content")
                                    .query(query)
                                    // .operator(Operator.And) // 如需更高精度可打开
                            )
                    )
                    .size(recallK),
                    ChatTextDocument.class
            );

            long total = response.hits().total() != null ? response.hits().total().value() : -1L;
            logger.debug("BM25 召回完成，ES 命中总数: {}, 实际返回: {}", total, response.hits().hits().size());

            List<ChatTextSearchResult> results = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                ChatTextDocument src = hit.source();
                if (src != null) {
                    ChatTextSearchResult r = new ChatTextSearchResult();
                    r.setId(src.getId());
                    r.setContent(src.getContent());
                    r.setScore(hit.score());
                    r.setUserId(src.getUserId());
                    r.setUserName(src.getUserName());
                    r.setGroupId(src.getGroupId());
                    r.setScope(src.getScope());
                    r.setCreateTime(src.getCreateTime());
                    results.add(r);
                }
            });
            return results;
        } catch (Exception e) {
            logger.error("BM25 召回失败", e);
            return Collections.emptyList();
        }
    }

    private List<ChatTextSearchResult> textOnlySearch(String query, int topK) throws Exception {
        SearchResponse<ChatTextDocument> response = esClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q
                        .match(m -> m
                                .field("content")
                                .query(query)
                        )
                )
                .size(topK),
                ChatTextDocument.class
        );

        List<ChatTextSearchResult> results = new ArrayList<>();
        response.hits().hits().forEach(hit -> {
            ChatTextDocument src = hit.source();
            if (src != null) {
                ChatTextSearchResult r = new ChatTextSearchResult();
                r.setId(src.getId());
                r.setContent(src.getContent());
                r.setScore(hit.score());
                r.setUserId(src.getUserId());
                r.setUserName(src.getUserName());
                r.setGroupId(src.getGroupId());
                r.setScope(src.getScope());
                r.setCreateTime(src.getCreateTime());
                results.add(r);
            }
        });
        return results;
    }

    /**
     * 生成查询向量，返回 List<Float>，失败时返回 null
     */
    private List<Float> embedToVectorList(String text) {
        try {
            float[] raw = embeddingClient.embed(text);
            if (raw == null || raw.length == 0) {
                logger.warn("生成的向量为空");
                return null;
            }
            List<Float> list = new ArrayList<>(raw.length);
            for (float v : raw) {
                list.add(v);
            }
            return list;
        } catch (Exception e) {
            logger.error("生成向量失败", e);
            return null;
        }
    }

    /**
     * 使用结果中的 fileMd5 作为 docId（当前 fileMd5 实际承载的是文档ID）
     */
    private String buildDocId(ChatTextSearchResult r) {
        return r.getId();
    }
    private List<ChatTextSearchResult> rrfMerge(List<String> knnDocIds,
                                                List<String> bm25DocIds,
                                                Map<String, ChatTextSearchResult> docMap,
                                                int topK) {
        Map<String, Double> scoreMap = new HashMap<>();

        for (int i = 0; i < knnDocIds.size(); i++) {
            String docId = knnDocIds.get(i);
            double contribution = 1.0d / (RRF_K + i + 1);
            scoreMap.merge(docId, contribution, Double::sum);
        }

        for (int i = 0; i < bm25DocIds.size(); i++) {
            String docId = bm25DocIds.get(i);
            double contribution = 1.0d / (RRF_K + i + 1);
            scoreMap.merge(docId, contribution, Double::sum);
        }

        return scoreMap.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed())
                .limit(topK)
                .map(entry -> {
                    ChatTextSearchResult r = docMap.get(entry.getKey());
                    if (r == null) {
                        logger.warn("在 docMap 中未找到 docId 对应的 ChatTextSearchResult，docId = {}", entry.getKey());
                        return null;
                    }
                    r.setScore(entry.getValue());
                    return r;
                })
                .filter(Objects::nonNull)
                .toList();
    }

}
