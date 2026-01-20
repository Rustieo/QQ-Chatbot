package rustie.qqchat.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import rustie.qqchat.mybatis.JsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@TableName(value = "group_chat_message", autoResultMap = true)
public class GroupChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    /**
     * Sender member id for user messages; may be null for assistant/system messages.
     */
    private Long groupMember;

    private String role;

    private String message;

//    @TableField(typeHandler = JsonbTypeHandler.class)
//    private Map<String, Object> meta;

    private OffsetDateTime createTime;
}

