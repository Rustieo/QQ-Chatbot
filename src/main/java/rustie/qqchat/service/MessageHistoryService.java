package rustie.qqchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import rustie.qqchat.model.dto.ChatMessage;
import rustie.qqchat.model.entity.GroupChatMessage;
import rustie.qqchat.model.entity.PrivateChatMessage;
import rustie.qqchat.mapper.GroupChatMessageMapper;
import rustie.qqchat.mapper.PrivateChatMessageMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class MessageHistoryService {
    private static final int MAX_HISTORY_ITEMS = 20;

    private final PrivateChatMessageMapper privateChatMessageMapper;
    private final GroupChatMessageMapper groupChatMessageMapper;
    private final Cache<String, List<ChatMessage>> messageHistoryCache;

    public List<ChatMessage> getPrivateHistory(long userId) {
        return messageHistoryCache.get(privateKey(userId), k -> loadPrivateHistoryFromDb(userId));
    }
    public List<ChatMessage> getGroupHistory(long groupId) {
        return messageHistoryCache.get(groupKey(groupId), k -> loadGroupHistoryFromDb(groupId));
    }
    @Transactional
    public void savePrivateTurn(long userId, String userMessage, String assistantMessage) {
        insertPrivateMessage(userId, "user", userMessage);
        insertPrivateMessage(userId, "assistant", assistantMessage);
        String key = privateKey(userId);
        List<ChatMessage> current = messageHistoryCache.getIfPresent(key);
        List<ChatMessage> updated = (current == null)
                ? loadPrivateHistoryFromDb(userId)
                : appendHistory(current, List.of(msg("user", userMessage), msg("assistant", assistantMessage)));
        messageHistoryCache.put(key, updated);
    }

    @Transactional
    public void saveGroupTurn(long groupId, long groupMemberId, String userMessage, String assistantMessage) {
        insertGroupMessage(groupId, groupMemberId, "user", userMessage);
        insertGroupMessage(groupId, 0L, "assistant", assistantMessage);
        String key = groupKey(groupId);
        List<ChatMessage> current = messageHistoryCache.getIfPresent(key);
        List<ChatMessage> updated = (current == null)
                ? loadGroupHistoryFromDb(groupId)
                : appendHistory(current, List.of(msg("user", userMessage), msg("assistant", assistantMessage)));
        messageHistoryCache.put(key, updated);
    }
    @Transactional
    public void clearPrivateHistory(long userId) {
        privateChatMessageMapper.delete(new LambdaQueryWrapper<PrivateChatMessage>()
                .eq(PrivateChatMessage::getUserId, userId));
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

    private List<ChatMessage> loadPrivateHistoryFromDb(long userId) {
        List<PrivateChatMessage> rows = privateChatMessageMapper.selectList(new LambdaQueryWrapper<PrivateChatMessage>()
                .eq(PrivateChatMessage::getUserId, userId)
                .orderByDesc(PrivateChatMessage::getId)
                .last("limit " + MAX_HISTORY_ITEMS));
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // reverse to ascending time order for LLM
        List<ChatMessage> history = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            PrivateChatMessage r = rows.get(i);
            history.add(msg(r.getRole(), r.getMessage()));
        }
        return history;
    }

    private List<ChatMessage> loadGroupHistoryFromDb(long groupId) {
        List<GroupChatMessage> rows = groupChatMessageMapper.selectList(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getGroupId, groupId)
                .orderByDesc(GroupChatMessage::getId)
                .last("limit " + MAX_HISTORY_ITEMS));
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // reverse to ascending time order for LLM
        List<ChatMessage> history = new ArrayList<>(rows.size());
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
//        row.setMeta(Map.of());
        row.setCreateTime(OffsetDateTime.now());
        privateChatMessageMapper.insert(row);
    }

    private void insertGroupMessage(long groupId, long groupMember, String role, String message) {
        GroupChatMessage row = new GroupChatMessage();
        row.setGroupId(groupId);
        row.setGroupMember(groupMember);
        row.setRole(role);
        row.setMessage(message);
//        row.setMeta(Map.of());
        row.setCreateTime(OffsetDateTime.now());
        groupChatMessageMapper.insert(row);
    }

    private static ChatMessage msg(String role, String content) {
        return new ChatMessage(role, content);
    }

    private static List<ChatMessage> appendHistory(List<ChatMessage> current,
                                                  List<ChatMessage> additions) {
        int currentSize = current != null ? current.size() : 0;
        int addSize = additions != null ? additions.size() : 0;
        if (currentSize == 0 && addSize == 0) return Collections.emptyList();

        List<ChatMessage> merged = new ArrayList<>(currentSize + addSize);
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

