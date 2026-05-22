package org.puregxl.site.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.antlr.runtime.tree.Tree;

import java.time.Duration;
import java.util.*;

public class RagMcpClientDemo {

    private static final String MCP_SERVER_BASE_URL = "http://localhost:8081";
    private static final String MCP_SSE_ENDPOINT = "/sse";

    public static void main(String[] args) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(MCP_SERVER_BASE_URL)
                .sseEndpoint(MCP_SSE_ENDPOINT)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(20))
                .build()) {

            client.initialize();

            System.out.println("=== Server Info ===");
            System.out.println(client.getServerInfo());
            System.out.println(client.getServerInstructions());

            System.out.println("\n=== Tools ===");
            McpSchema.ListToolsResult tools = client.listTools();
            for (McpSchema.Tool tool : tools.tools()) {
                System.out.println(tool.name() + " - " + tool.description());
                System.out.println("  inputSchema=" + tool.inputSchema());
            }

            callAndPrint(client, "rag_ping", Map.of());
            callAndPrint(client, "rag_search", Map.of(
                    "query", "缓考申请需要什么材料",
                    "topK", 3
            ));
            callAndPrint(client, "rag_answer", Map.of(
                    "question", "缓考申请需要什么材料",
                    "topK", 3
            ));
        }
    }

    private static void callAndPrint(McpSyncClient client, String toolName, Map<String, Object> arguments) {
        System.out.println("\n=== Call Tool: " + toolName + " ===");
        McpSchema.CallToolResult result = client.callTool(
                McpSchema.CallToolRequest.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .build()
        );

        System.out.println("isError=" + result.isError());
        System.out.println("structuredContent=" + result.structuredContent());
        System.out.println("content=" + result.content());
    }








}
