package rustie.qqchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rustie.qqchat.entity.GroupChatMessage;
import rustie.qqchat.entity.PrivateChatMessage;
import rustie.qqchat.mapper.GroupChatMessageMapper;
import rustie.qqchat.mapper.PrivateChatMessageMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Message persistence + local conversation history (Caffeine).
 *
 * Persistence order for each write: DB first, then Caffeine.
 */
@Service
public class MessageHistoryService {

    /**
     * How many message items are kept for LLM history.
     * Note: one "turn" usually produces 2 items (user + assistant).
     */
    private static final int MAX_HISTORY_ITEMS = 20;

    private final PrivateChatMessageMapper privateChatMessageMapper;
    private final GroupChatMessageMapper groupChatMessageMapper;
    private final Cache<String, List<Map<String, String>>> messageHistoryCache;

    public MessageHistoryService(PrivateChatMessageMapper privateChatMessageMapper,
                                 GroupChatMessageMapper groupChatMessageMapper,
                                 Cache<String, List<Map<String, String>>> messageHistoryCache) {
        this.privateChatMessageMapper = privateChatMessageMapper;
        this.groupChatMessageMapper = groupChatMessageMapper;
        this.messageHistoryCache = messageHistoryCache;
    }

    public List<Map<String, String>> getPrivateHistory(long userId) {
        return messageHistoryCache.get(privateKey(userId), k -> loadPrivateHistoryFromDb(userId));
    }

    public List<Map<String, String>> getGroupHistory(long groupId) {
        return messageHistoryCache.get(groupKey(groupId), k -> loadGroupHistoryFromDb(groupId));
    }

    @Transactional
    public void savePrivateTurn(long userId, String userMessage, String assistantMessage) {
        // 1) DB first
        insertPrivateMessage(userId, "user", userMessage);
        insertPrivateMessage(userId, "assistant", assistantMessage);

        // 2) then Caffeine
        String key = privateKey(userId);
        List<Map<String, String>> current = messageHistoryCache.getIfPresent(key);
        // 如果当前缓存不存在，为了不丢历史，回填 DB 最后 N 条（已包含刚插入的两条）
        List<Map<String, String>> updated = (current == null)
                ? loadPrivateHistoryFromDb(userId)
                : appendHistory(current, List.of(msg("user", userMessage), msg("assistant", assistantMessage)));
        messageHistoryCache.put(key, updated);
    }

    @Transactional
    public void saveGroupTurn(long groupId, long groupMemberId, String userMessage, String assistantMessage) {
        // 1) DB first
        insertGroupMessage(groupId, groupMemberId, "user", userMessage);
        // assistant message: group_member is NOT NULL in ddl, use 0 as bot marker
        insertGroupMessage(groupId, 0L, "assistant", assistantMessage);

        // 2) then Caffeine
        String key = groupKey(groupId);
        List<Map<String, String>> current = messageHistoryCache.getIfPresent(key);
        List<Map<String, String>> updated = (current == null)
                ? loadGroupHistoryFromDb(groupId)
                : appendHistory(current, List.of(msg("user", userMessage), msg("assistant", assistantMessage)));
        messageHistoryCache.put(key, updated);
    }

    @Transactional
    public void clearPrivateHistory(long userId) {
        // DB
        privateChatMessageMapper.delete(new LambdaQueryWrapper<PrivateChatMessage>()
                .eq(PrivateChatMessage::getUserId, userId));
        // cache
        messageHistoryCache.invalidate(privateKey(userId));
    }

    @Transactional
    public void clearGroupHistory(long groupId) {
        // DB
        groupChatMessageMapper.delete(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getGroupId, groupId));
        // cache
        messageHistoryCache.invalidate(groupKey(groupId));
    }

    private List<Map<String, String>> loadPrivateHistoryFromDb(long userId) {
        List<PrivateChatMessage> rows = privateChatMessageMapper.selectList(new LambdaQueryWrapper<PrivateChatMessage>()
                .eq(PrivateChatMessage::getUserId, userId)
                .orderByDesc(PrivateChatMessage::getId)
                .last("limit " + MAX_HISTORY_ITEMS));
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // reverse to ascending time order for LLM
        List<Map<String, String>> history = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            PrivateChatMessage r = rows.get(i);
            history.add(msg(r.getRole(), r.getMessage()));
        }
        return history;
    }

    private List<Map<String, String>> loadGroupHistoryFromDb(long groupId) {
        List<GroupChatMessage> rows = groupChatMessageMapper.selectList(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getGroupId, groupId)
                .orderByDesc(GroupChatMessage::getId)
                .last("limit " + MAX_HISTORY_ITEMS));
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // reverse to ascending time order for LLM
        List<Map<String, String>> history = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            GroupChatMessage r = rows.get(i);
            history.add(msg(r.getRole(), r.getMessage()));
        }
        return history;
    }

    private void insertPrivateMessage(long userId, String role, String message) {
        PrivateChatMessage row = new PrivateChatMessage();
        row.setUserId(userId);
        row.setRole(role);
        row.setMessage(message);
        row.setMeta(Map.of());
        row.setCreateTime(OffsetDateTime.now());
        privateChatMessageMapper.insert(row);
    }

    private void insertGroupMessage(long groupId, long groupMember, String role, String message) {
        GroupChatMessage row = new GroupChatMessage();
        row.setGroupId(groupId);
        row.setGroupMember(groupMember);
        row.setRole(role);
        row.setMessage(message);
        row.setMeta(Map.of());
        row.setCreateTime(OffsetDateTime.now());
        groupChatMessageMapper.insert(row);
    }

    private static Map<String, String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static List<Map<String, String>> appendHistory(List<Map<String, String>> current,
                                                          List<Map<String, String>> additions) {
        int currentSize = current != null ? current.size() : 0;
        int addSize = additions != null ? additions.size() : 0;
        if (currentSize == 0 && addSize == 0) return Collections.emptyList();

        List<Map<String, String>> merged = new ArrayList<>(currentSize + addSize);
        if (currentSize > 0) merged.addAll(current);
        if (addSize > 0) merged.addAll(additions);

        if (merged.size() <= MAX_HISTORY_ITEMS) return merged;
        return merged.subList(merged.size() - MAX_HISTORY_ITEMS, merged.size());
    }

    private static String privateKey(long userId) {
        return "p:" + userId;
    }

    private static String groupKey(long groupId) {
        return "g:" + groupId;
    }
}

