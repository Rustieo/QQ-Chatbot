package rustie.qqchat.utils;

import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;

import java.util.List;
import java.util.Map;

public class ChatUtils {
    private static final String DEFAULT_RETURN="";
    public static String getEventPlainText(MessageEvent event){
        List<ArrayMsg>list= ShiroUtils.rawToArrayMsg(event.getMessage());
        for (ArrayMsg msg : list) {
            if (msg.getType() == MsgTypeEnum.text) {
                String textData = msg.getData().get("text");
                if (textData != null) {
                    return textData;
                }
            }
        }
        return DEFAULT_RETURN;
    }
}
