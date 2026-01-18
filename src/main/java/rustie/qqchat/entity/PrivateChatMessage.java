package rustie.qqchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import rustie.qqchat.mybatis.JsonbTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@TableName(value = "private_chat_message", autoResultMap = true)
public class PrivateChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String role;

    private String message;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> meta;

    private OffsetDateTime createTime;
}

