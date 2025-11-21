package rustie.qqchat.service;

import org.springframework.stereotype.Service;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.service.ChatService;

import java.util.regex.Pattern;

@Service
public class SettingService {

    public static final String SYSPROMPT_COMMAND = "/setting/ds/sysprompt";
    public static final String TOP_P_COMMAND = "/setting/ds/topp";
    public static final String TEMPERATURE_COMMAND = "/setting/ds/temperature";

    private final AiProperties aiProperties;
    private final ChatService chatService;

    public SettingService(AiProperties aiProperties, ChatService chatService) {
        this.aiProperties = aiProperties;
        this.chatService = chatService;
    }

    public CommandResult updateSystemPrompt(String rawMessage) {
        String systemRole = extractPayload(rawMessage, SYSPROMPT_COMMAND);
        if (systemRole.isEmpty()) {
            return CommandResult.failure("请在命令后附上系统角色内容");
        }
        aiProperties.getPrompt().setRules(systemRole);
        chatService.clearHistory();
        return CommandResult.success("系统角色已更新");
    }

    public CommandResult updateTopP(String rawMessage) {
        String payload = extractPayload(rawMessage, TOP_P_COMMAND);
        if (payload.isEmpty()) {
            return CommandResult.failure("请提供 0-1 之间的 top_p 数值");
        }
        try {
            double value = Double.parseDouble(payload);
            if (value < 0 || value > 1) {
                return CommandResult.failure("top_p 必须在 0 到 1 之间");
            }
            aiProperties.getGeneration().setTopP(value);
            chatService.clearHistory();
            return CommandResult.success(String.format("top_p 已更新为 %.2f", value));
        } catch (NumberFormatException ex) {
            return CommandResult.failure("无法解析 top_p，请输入合法的小数");
        }
    }

    public CommandResult updateTemperature(String rawMessage) {
        String payload = extractPayload(rawMessage, TEMPERATURE_COMMAND);
        if (payload.isEmpty()) {
            return CommandResult.failure("请提供 0-2 之间的 temperature 数值");
        }
        try {
            double value = Double.parseDouble(payload);
            if (value < 0 || value > 2) {
                return CommandResult.failure("temperature 必须在 0 到 2 之间");
            }
            aiProperties.getGeneration().setTemperature(value);
            chatService.clearHistory();
            return CommandResult.success(String.format("Temperature 已更新为 %.2f", value));
        } catch (NumberFormatException ex) {
            return CommandResult.failure("无法解析 temperature，请输入合法的小数");
        }
    }

    public String buildHelpMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("【基础对话】\n");
        sb.append("[群] /chat 普通对话。\n");
        sb.append("[群] /clear 描述  清空历史记录。");
        sb.append("[群] /new  描述  开启新对话。");
        sb.append("------\n");

        sb.append("【RAG 问答】\n");
        sb.append("[群] /rq 问题内容  使用知识库进行回答。\n");
        sb.append("------\n");
        sb.append("【角色 & 系统设置】\n");
        sb.append("[群] /role 描述  让 DeepSeek 根据描述生成系统提示词并更新。\n\n");
        sb.append("示例： /role 哈基米是一种喜欢哈气的生物....\n\n");
        sb.append("[群] ").append(SYSPROMPT_COMMAND).append(" 文本  直接设置系统提示词。\n\n");
        sb.append("示例：").append(SYSPROMPT_COMMAND).append(" 你是一个火鸡面。\n\n");
        sb.append("[群] ").append(TOP_P_COMMAND).append(" 0-1  设置 top_p。\n\n");
        sb.append("[群] ").append(TEMPERATURE_COMMAND).append(" 0-2  设置 temperature。\n");
        sb.append("------\n");
        sb.append("【知识库管理（RAG）】\n");
        sb.append("[群] /rag/add/common 文本  将文本加入知识库（不带昵称等元信息）。\n\n");
        sb.append("[群] /rag/add/meta 文本  将文本加入知识库，并记录用户信息。\n\n");
        sb.append("[群] /rag/rm  清空知识库索引）。\n");
        sb.append("------\n");
        sb.append("【其他】\n");
        sb.append("[群] /help  显示本帮助信息。\n");

        return sb.toString();
    }

    private String extractPayload(String message, String command) {
        return message.replaceFirst("(?i)^" + Pattern.quote(command) + "\\s*", "").trim();
    }

    public record CommandResult(boolean success, String message) {
        public static CommandResult success(String message) {
            return new CommandResult(true, message);
        }

        public static CommandResult failure(String message) {
            return new CommandResult(false, message);
        }
    }
}
