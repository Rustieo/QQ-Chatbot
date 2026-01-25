package rustie.qqchat.agent;


import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public interface Tool {
    String name();
    String description();
    /**
     * If true, the agent should return the tool result to the user directly,
     * and MUST NOT send the tool output back to the LLM.
     */
    default boolean returnDirect() { return false; }
    /**
     * If false, the agent will NOT send the tool output back to the LLM (as a "tool" message).
     * Note: if returnDirect() is true, this flag is ignored (it will not be sent anyway).
     */
    default boolean includeResultInModelContext() { return true; }
    /**
     * Render tool execution result as user-visible text when returnDirect() is true.
     */
    default String toUserText(JsonNode result, ObjectMapper om) throws Exception {
        return om.writeValueAsString(result);
    }
    default ObjectNode parameters(ObjectMapper om){
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", om.createObjectNode());
        schema.set("required", om.createArrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }
    JsonNode execute(JsonNode args, ObjectMapper om) throws Exception;
}

