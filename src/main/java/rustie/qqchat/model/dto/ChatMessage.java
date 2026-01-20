package rustie.qqchat.model.dto;

/**
 * Conversation history item used by cache/DB loaders.
 * Keep it minimal and OpenAI-compatible: role + content.
 */
public record ChatMessage(String role, String content) {}
