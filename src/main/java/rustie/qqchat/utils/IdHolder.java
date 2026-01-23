package rustie.qqchat.utils;

public class IdHolder {
    private final static ThreadLocal<Long> groupId = new ThreadLocal<>();
    private final static ThreadLocal<Long> privateId = new ThreadLocal<>();
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
    public static void removeAll() {
        removeGroupId();
        removePrivateId();
    }
}
