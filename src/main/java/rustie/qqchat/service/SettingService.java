package rustie.qqchat.service;

import org.springframework.stereotype.Service;
import rustie.qqchat.config.AiProperties;
import rustie.qqchat.client.ModelType;
import rustie.qqchat.client.LLMClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SettingService {

    public static final String SYSPROMPT_COMMAND = "/set/ai/sysprompt";
    public static final String TOP_P_COMMAND = "/set/ai/topp";
    public static final String TEMPERATURE_COMMAND = "/set/ai/temperature";
    public static final String LIMIT_COMMAND = "/set/ai/limit"; // 保留兼容（整体替换占位）
    // 新增: 行为规则管理子命令
    public static final String LIMIT_ADD_COMMAND = "/set/ai/limit/add";
    public static final String LIMIT_DEL_COMMAND = "/set/ai/limit/del";
    public static final String LIMIT_LIST_COMMAND = "/set/ai/limit/list";
    public static final String LIMIT_CLEAR_COMMAND = "/set/ai/limit/clear";
    // 新增: 模型切换命令
    public static final String MODEL_SWITCH_COMMAND = "/set/ai/model";

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
        aiProperties.getPrompt().setRoles(systemRole);
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

    // ================= 行为规则（limits）管理 =================
    public CommandResult addLimit(String rawMessage) {
        String payload = extractPayload(rawMessage, LIMIT_ADD_COMMAND);
        if (payload.isEmpty()) {
            return CommandResult.failure("请在命令后提供要添加的行为规则文本");
        }
        List<String> limits = getOrInitLimits();
        // 去重：如果已存在，给出提示，不重复添加
        for (int i = 0; i < limits.size(); i++) {
            if (limits.get(i).equals(payload)) {
                return CommandResult.failure("该规则已存在，序号为 " + (i + 1));
            }
        }
        limits.add(payload);
        chatService.clearHistory();
        return CommandResult.success("已添加行为规则，当前共 " + limits.size() + " 条");
    }

    public CommandResult deleteLimit(String rawMessage) {
        String payload = extractPayload(rawMessage, LIMIT_DEL_COMMAND);
        if (payload.isEmpty()) {
            return CommandResult.failure("请提供要删除的规则序号或完整文本");
        }
        List<String> limits = getOrInitLimits();
        if (limits.isEmpty()) {
            return CommandResult.failure("当前没有任何规则可删除");
        }
        // 尝试按序号删除
        try {
            int idx = Integer.parseInt(payload.trim());
            if (idx < 1 || idx > limits.size()) {
                return CommandResult.failure("序号超出范围，当前规则数量为 " + limits.size());
            }
            String removed = limits.remove(idx - 1);
            chatService.clearHistory();
            return CommandResult.success("已按序号删除: " + removed);
        } catch (NumberFormatException ignore) {
            // 不是数字，则按内容匹配删除第一条
        }
        for (int i = 0; i < limits.size(); i++) {
            if (limits.get(i).equals(payload)) {
                limits.remove(i);
                chatService.clearHistory();
                return CommandResult.success("已按文本删除，剩余 " + limits.size() + " 条");
            }
        }
        return CommandResult.failure("未找到匹配的规则（序号或文本）");
    }

    public CommandResult listLimits() {
        List<String> limits = aiProperties.getPrompt().getLimits();
        if (limits == null || limits.isEmpty()) {
            return CommandResult.success("当前无行为规则");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前行为规则共 ").append(limits.size()).append(" 条:\n");
        for (int i = 0; i < limits.size(); i++) {
            sb.append(i + 1).append(". ").append(limits.get(i)).append("\n");
        }
        return CommandResult.success(sb.toString());
    }

    public CommandResult clearLimits() {
        List<String> limits = aiProperties.getPrompt().getLimits();
        if (limits == null || limits.isEmpty()) {
            return CommandResult.failure("当前没有规则可清空");
        }
        limits.clear();
        chatService.clearHistory();
        return CommandResult.success("已清空所有行为规则");
    }

    private List<String> getOrInitLimits() {
        List<String> limits = aiProperties.getPrompt().getLimits();
        if (limits == null) {
            limits = new ArrayList<>();
            aiProperties.getPrompt().setLimits(limits);
        }
        return limits;
    }

    // ================== 模型切换 ==================
    public CommandResult switchModel(String rawMessage, LLMClient llmClient) {
        String payload = extractPayload(rawMessage, MODEL_SWITCH_COMMAND);
        if (payload.isEmpty()) {
            return CommandResult.failure("请提供模型名称，如 deepseek 或 qwen");
        }
        String name = payload.toLowerCase();
        ModelType type = null;
        if (name.startsWith("deep")|| name.startsWith("ds")) {
            type = ModelType.DeepSeek;
        } else if (name.startsWith("qwen") || name.startsWith("qw")) {
            type = ModelType.Qwen;
        }
        if (type == null) {
            return CommandResult.failure("未知模型名称: " + payload);
        }
        boolean ok = llmClient.switchModel(type);
        if (!ok) {
            return CommandResult.failure("模型切换失败: 未配置 " + type);
        }
        chatService.clearHistory();
        return CommandResult.success("已切换到模型: " + type);
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
        sb.append("[群] /rq/ex 问题内容  使用知识库进行回答,并输出执行流程 \n");
        sb.append("------\n");
        sb.append("【角色 & 系统设置】\n");
        sb.append("[群] /role 描述  让 DeepSeek 根据描述生成系统提示词并更新。\n\n");
        sb.append("示例： /role 哈基米是一种喜欢哈气的生物....\n\n");
        sb.append("[群] ").append(SYSPROMPT_COMMAND).append(" 文本  直接设置系统提示词。\n\n");
        sb.append("示例：").append(SYSPROMPT_COMMAND).append(" 你是一个火鸡面。\n\n");
        sb.append("[群] ").append(TOP_P_COMMAND).append(" 0-1  设置 top_p。\n\n");
        sb.append("[群] ").append(TEMPERATURE_COMMAND).append(" 0-2  设置 temperature。\n");
        sb.append("------\n");
        sb.append("【行为规则管理】\n");
        sb.append("用于限制 AI 的回答风格/安全/语气等，可叠加多条。\n");
        sb.append("[群] ").append(LIMIT_ADD_COMMAND).append(" 规则文本  添加一条行为规则。\n\n");
        sb.append("[群] ").append(LIMIT_DEL_COMMAND).append(" 序号|完整文本  删除一条行为规则。\n\n");
        sb.append("示例：").append(LIMIT_DEL_COMMAND).append(" 2  (按序号删除第2条)\n\n");
        sb.append("示例：").append(LIMIT_DEL_COMMAND).append(" 不要泄露用户个人信息。 (按文本删除)\n\n");
        sb.append("[群] ").append(LIMIT_LIST_COMMAND).append("  查看当前全部规则。\n\n");
        sb.append("[群] ").append(LIMIT_CLEAR_COMMAND).append("  清空全部行为规则。\n\n");
        sb.append("------\n");
        sb.append("【模型切换】\n");
        sb.append("[群] ").append(MODEL_SWITCH_COMMAND).append(" (deepseek/ds)|(qwen/qw)  切换当前使用的模型。\n\n");;
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
