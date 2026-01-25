package rustie.qqchat.utils;

import java.util.List;

/**
 * Lightweight intent detector for "generate/edit image" vs "understand image".
 * Keep it simple and conservative to avoid wrong routing.
 */
public final class ImageIntentUtils {
    private ImageIntentUtils() {}

    public static boolean shouldGenImage(String userText, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return false;
        String t = userText == null ? "" : userText.trim().toLowerCase();
        if (t.isBlank()) return false;

        // Explicit keywords for generating / editing images.
        String[] kws = {
                "生图", "生成图", "生成图片", "画", "画一张", "画个", "做图",
                "改图", "修图", "p图", "换装", "换衣", "换成", "替换", "把", "改成",
                "风格", "变成", "去掉", "抠图", "加上", "合成", "扩图", "高清", "超清"
        };

        for (String kw : kws) {
            if (!kw.isBlank() && t.contains(kw)) return true;
        }

        // Common editing phrasing with images attached.
        if (t.contains("把图") || t.contains("这张图") || t.contains("图1") || t.contains("图2")) return true;
        return false;
    }
}

