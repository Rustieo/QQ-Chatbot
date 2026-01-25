package rustie.qqchat.utils;

import java.util.List;

public class IdHolder {
    private final static ThreadLocal<Long> groupId = new ThreadLocal<>();
    private final static ThreadLocal<Long> privateId = new ThreadLocal<>();
    private final static ThreadLocal<List<String>> imageUrls = new ThreadLocal<>();
    public static void setGroupId(Long id) {
        groupId.set(id);
    }
    public static Long getGroupId() {
        return groupId.get();
    }
    public static void removeGroupId() {
        groupId.remove();
    }
    public static void setPrivateId(Long id) {
        privateId.set(id);
    }
    public static Long getPrivateId() {
        return privateId.get();
    }
    public static void removePrivateId() {
        privateId.remove();
    }
    public static void setImageUrls(List<String> urls) {
        imageUrls.set(urls);
    }
    public static List<String> getImageUrls() {
        return imageUrls.get();
    }
    public static void removeImageUrls() {
        imageUrls.remove();
    }
    public static void removeAll() {
        removeGroupId();
        removePrivateId();
        removeImageUrls();
    }
}
