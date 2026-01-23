package rustie.qqchat.agent;


import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public interface Tool {
    String name();
    String description();
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

