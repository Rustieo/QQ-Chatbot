package rustie.qqchat.entity;

import lombok.Data;

@Data
public class ChatText {
    private String content;
    private String userId;
    private String userName;
    private String groupId;
    private String scope; // "global", "group", "private"
    private Long createTime;
}
