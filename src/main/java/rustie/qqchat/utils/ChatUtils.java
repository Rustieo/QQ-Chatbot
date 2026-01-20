package rustie.qqchat.utils;

import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;

import java.util.List;

public class ChatUtils {
    private static final String DEFAULT_RETURN="";
    public static String getEventPlainText(MessageEvent event){
        List<ArrayMsg> list = event.getArrayMsg() != null ? event.getArrayMsg() : ShiroUtils.rawToArrayMsg(event.getMessage());
        if (list == null || list.isEmpty()) return DEFAULT_RETURN;
        StringBuilder sb = new StringBuilder();
        for (ArrayMsg msg : list) {
            if (msg == null || msg.getType() != MsgTypeEnum.text) continue;
            String textData = msg.getData() != null ? msg.getData().get("text") : null;
            if (textData != null && !textData.isBlank()) sb.append(textData);
        }
        return sb.toString();
    }

    public static List<String> getEventImageUrls(MessageEvent event) {
        List<ArrayMsg> list = event.getArrayMsg() != null ? event.getArrayMsg() : ShiroUtils.rawToArrayMsg(event.getMessage());
        if (list == null || list.isEmpty()) return List.of();
        return ShiroUtils.getMsgImgUrlList(list);
    }
}
