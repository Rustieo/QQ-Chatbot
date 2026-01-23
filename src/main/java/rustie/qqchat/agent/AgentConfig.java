package rustie.qqchat.agent;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rustie.qqchat.client.LLMClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AgentConfig {

//    @Bean
//    public ToolRegistry toolRegistry(ObjectProvider<McpConnector> mcpConnectorProvider) {
//        var tools = new ArrayList<Tool>();
//        tools.add(new GetTimeTool());
//        tools.add(new AddTool());
//        tools.add(new EchoTool());
//
//        McpConnector mcpConnector = mcpConnectorProvider.getIfAvailable();
//        if (mcpConnector != null) tools.addAll(mcpConnector.tools());
//
//        return new ToolRegistry(tools);
//    }

//    @Bean(destroyMethod = "close")
//    @ConditionalOnProperty(name = "mcp.enabled", havingValue = "true")
//    public McpConnector mcpConnector(
//            OkHttpClient http,
//            ObjectMapper om,
//            @Value("${mcp.required:false}") boolean mcpRequired,
//            @Value("${mcp.tool-prefix:mcp.}") String toolPrefix,
//            @Value("${mcp.protocol-version:2024-11-05}") String protocolVersion,
//            @Value("${mcp.client-name:RustAgent}") String clientName,
//            @Value("${mcp.client-version:0.0.1}") String clientVersion,
//            @Value("${mcp.stdio.command:}") String mcpCommand,
//            @Value("${mcp.stdio.args:}") String mcpArgs,
//            @Value("${mcp.stdio.framing:content-length}") String framing,
//            @Value("${mcp.timeout-ms:15000}") long timeoutMs
//    ) {
//        try {
//            return McpConnector.stdio(http, om,
//                    protocolVersion,
//                    clientName,
//                    clientVersion,
//                    toolPrefix,
//                    mcpCommand,
//                    mcpArgs,
//                    framing,
//                    Duration.ofMillis(timeoutMs)
//            );
//        } catch (Exception e) {
//            if (mcpRequired) throw new IllegalStateException("Failed to initialize MCP connector", e);
//            return McpConnector.disabled(om);
//        }
//    }

    @Bean
    public ReActAgent reActAgent(LLMClient client, ToolRegistry registry, ObjectMapper om) {
        return new ReActAgent(client, registry, om);
    }
}