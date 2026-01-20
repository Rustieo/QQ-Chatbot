package rustie.qqchat.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rustie.qqchat.model.dto.ChatMessage;

import java.time.Duration;
import java.util.List;

@Configuration
public class MessageHistoryCacheConfig {

    /**
     * Conversation history cache.
     *
     * Key format:
     * - private: "p:{userId}"
     * - group:   "g:{groupId}"
     *
     * Value: OpenAI-compatible message list items: {"role": "...", "content": "..."}
     */
    @Bean
    public Cache<String, List<ChatMessage>> messageHistoryCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(12))
                .build();
    }
}

