package rustie.qqchat.model.entity;

import lombok.Data;

@Data
public class ChatTextDocument {
    private String id;          // ES 的文档ID(目前用的是full_text的MD5生成)
   // private Integer chunkId;      // 文本块序号,目前不用,因为考虑一条消息通常不会很长
    private String content;     // 分片文本内容
    private float[] vector;     // 向量数据
    // 元数据字段
    private String userId;      // 所属用户 (可为 null)
    private String userName;
    private String groupId;     // 所属群聊 (可为 null)
    private String scope;       // "global" (全网通用), "group" (群通用), "private" (个人私有)
    private Long createTime;
}
