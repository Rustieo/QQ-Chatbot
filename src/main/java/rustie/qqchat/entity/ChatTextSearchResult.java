package rustie.qqchat.entity;

import lombok.Data;

@Data
public class ChatTextSearchResult {
    private String id;
    private String content;
    private Double score;
    private String userId;     // 上传用户ID
    private String userName;     // 上传用户ID
    private String groupId;    // 群组ID
    private String scope;      // "global" (全网通用), "group" (群通用), "private" (个人私有)
    private Long createTime;

}
