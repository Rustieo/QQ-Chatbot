package rustie.qqchat.utils;

import java.util.List;

/**
 * Lightweight intent detector for "generate/edit image" vs "understand image".
 * Keep it simple and conservative to avoid wrong routing.
 */
public final class ImageIntentUtils {
    private ImageIntentUtils() {}

    public static boolean shouldGenImage(String userText, List<String> imageUrls) {
        String t = userText == null ? "" : userText.trim().toLowerCase();
        if (t.isBlank()) return false;

        boolean hasImages = imageUrls != null && !imageUrls.isEmpty();

        // No-image: be conservative (text-to-image only).
        if (!hasImages) {
            String[] genOnly = {
                    "生图", "文生图", "生成图片", "生成一张", "生成图", "画一张", "画张", "画个", "画一幅", "绘制", "做一张图",
                    "generate an image", "text to image"
            };
            for (String kw : genOnly) {
                if (!kw.isBlank() && t.contains(kw)) return true;
            }
            return false;
        }

        // Has images: generation or editing intent.
        String[] genOrEdit = {
                "生图", "生成图", "生成图片", "画一张", "画张", "画个", "绘制", "做图",
                "改图", "修图", "p图", "换装", "换衣", "替换", "改成", "变成", "去掉", "抠图", "加上", "合成", "扩图", "高清", "超清"
        };
        for (String kw : genOrEdit) {
            if (!kw.isBlank() && t.contains(kw)) return true;
        }

        // Common editing phrasing with images attached.
        return t.contains("把图") || t.contains("这张图") || t.contains("图1") || t.contains("图2");
    }
}

