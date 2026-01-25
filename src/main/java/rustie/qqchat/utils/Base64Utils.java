package rustie.qqchat.utils;

import com.mikuac.shiro.common.utils.MsgUtils;

import java.nio.file.*;
import java.util.Base64;
import java.util.Random;

public class Base64Utils {
    /** Path → CQ 图片消息字符串 */
    public static String buildCqImageMessage(Path imagePath) {
        String abs = imagePath.toAbsolutePath().toString().replace("\\", "/");
        return MsgUtils.builder()
                .img("file:///" + abs)
                .build();
    }

    /** 从 base64 内容保存为临时图片文件，返回文件 Path */
    public static Path saveToTempFile(String base64OrDataUrl) throws Exception {
        String b64 = normalizeBase64(base64OrDataUrl);

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 data (decode failed)", e);
        }

        // 默认 png（你也可以从 dataURL 头里判断 jpg/png，这里先给最稳用法）
        Path img = Files.createTempFile("napcat_img_", ".png");
        Files.write(img, bytes, StandardOpenOption.TRUNCATE_EXISTING);

        return img;
    }

    /** 去掉 dataURL 头 + 去掉空白字符 */
    private static String normalizeBase64(String s) {
        if (s == null) return "";
        s = s.trim();

        // 如果形如 data:image/png;base64,xxxx
        int comma = s.indexOf(',');
        if (comma >= 0 && s.substring(0, comma).contains("base64")) {
            s = s.substring(comma + 1);
        }

        // 去掉换行/空格
        return s.replaceAll("\\s+", "");
    }
}
