package rustie.qqchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import rustie.qqchat.entity.PrivateChatMessage;

@Mapper
public interface PrivateChatMessageMapper extends BaseMapper<PrivateChatMessage> {
}

