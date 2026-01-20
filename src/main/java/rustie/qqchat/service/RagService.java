package rustie.qqchat.service;

////负责将消息转为ChatTextDocument,存入知识库
////也负责进行rag查询
//public class RagService {
//    private final ElasticsearchService elasticsearchService;
//    private final VectorizationService vectorizationService;
//    private final HybridSearchService hybridSearchService;
//    public RagService(ElasticsearchService elasticsearchService,
//                      VectorizationService vectorizationService,
//                      HybridSearchService hybridSearchService) {
//        this.elasticsearchService = elasticsearchService;
//        this.vectorizationService = vectorizationService;
//        this.hybridSearchService = hybridSearchService;
//    }
//    public void addChatTextNoMeta(String text){
//        //TODO:向量化后转为ChatTextDocument,存入知识库
//    }
//    public void addChatTextWithMeta(String text){
//        //TODO:向量化后转为ChatTextDocument,存入知识库
//    }
//
//}
