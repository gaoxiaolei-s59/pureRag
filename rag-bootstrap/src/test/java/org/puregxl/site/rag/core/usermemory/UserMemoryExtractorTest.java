package org.puregxl.site.rag.core.usermemory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.rag.support.PromptTemplateLoader;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserMemoryExtractorTest {
    private final LLMService model = mock(LLMService.class);
    private final UserMemoryExtractor extractor = new UserMemoryExtractor(model, new PromptTemplateLoader());

    @Test
    void appliesEditsAgainstOriginalPositionsUsingOnlyThisUserMessage() throws Exception {
        List<String> current = List.of("使用苹果手机", "常坐地铁", "喜欢黑咖啡");
        String source = "忘记我的手机。我现在改喝红茶了，也一直喜欢周末徒步。";
        when(model.chat(any(ChatRequest.class))).thenReturn("""
                {"edits":[
                  {"op":"delete","index":1,"quote":"忘记我的手机"},
                  {"op":"update","index":3,"fact":"喜欢红茶","quote":"我现在改喝红茶了"},
                  {"op":"add","fact":"喜欢周末徒步","quote":"一直喜欢周末徒步"}
                ]}
                """);
        assertThat(extractor.extract(current, source)).containsExactly("常坐地铁", "喜欢红茶", "喜欢周末徒步");

        ArgumentCaptor<ChatRequest> call = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(call.capture());
        List<ChatMessage> messages = call.getValue().getMessages();
        assertThat(messages).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER);
        assertThat(messages.get(0).getContent()).doesNotContain(source);
        JsonNode input = new ObjectMapper().readTree(messages.get(1).getContent());
        assertThat(input.get("userMessage").asText()).isEqualTo(source);
        assertThat(input.get("existingFacts").get(0).asText()).isEqualTo(current.get(0));
    }

    @Test
    void unrelatedMessageLeavesFactsIntactAndClearRequiresItsOwnDecision() {
        List<String> existing = List.of("喜欢绿茶");
        when(model.chat(any(ChatRequest.class)))
                .thenReturn("{\"edits\":[]}", "{\"edits\":[{\"op\":\"clear\",\"quote\":\"清空全部长期记忆\"}]}");
        assertThat(extractor.extract(existing, "这次给朋友买红茶")).isEqualTo(existing);
        assertThat(extractor.extract(existing, "请清空全部长期记忆")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"edits\":null}", "{\"edits\":[]} {}",
            "{\"edits\":[],\"edits\":[]}",
            "{\"edits\":[{\"op\":\"clear\",\"quote\":\"假的引文\"}]}",
            "{\"edits\":[{\"op\":\"delete\",\"index\":0,\"quote\":\"喝茶\"}]}",
            "{\"edits\":[{\"op\":\"delete\",\"index\":1,\"quote\":\"喝茶\"},{\"op\":\"update\",\"index\":1,\"fact\":\"喝水\",\"quote\":\"喝茶\"}]}",
            "{\"edits\":[{\"op\":\"clear\",\"quote\":\"喝茶\"},{\"op\":\"add\",\"fact\":\"喜欢喝茶\",\"quote\":\"喝茶\"}]}"
    })
    void rejectsMalformedOrUngroundedDecisions(String response) {
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
        assertThatIllegalStateException().isThrownBy(() -> extractor.extract(List.of("旧事实"), "喜欢喝茶"));
    }

    @Test
    void skipsOversizedSourceWithoutSendingPartialStatements() {
        assertThat(extractor.extract(List.of("旧事实"), "🤖".repeat(8001))).containsExactly("旧事实");
        verifyNoInteractions(model);
    }

    @Test
    void keepsRecentlyUpdatedFactWhenEvictingAtCapacity() {
        List<String> full = IntStream.rangeClosed(1, 32).mapToObj(i -> "事实" + i).toList();
        when(model.chat(any(ChatRequest.class))).thenReturn("""
                {"edits":[{"op":"update","index":1,"fact":"喜欢茶","quote":"喜欢茶"},
                          {"op":"add","fact":"周末徒步","quote":"周末徒步"}]}
                """);
        assertThat(extractor.extract(full, "我喜欢茶，周末徒步")).hasSize(32)
                .startsWith("喜欢茶", "事实3").endsWith("周末徒步");
    }
}
