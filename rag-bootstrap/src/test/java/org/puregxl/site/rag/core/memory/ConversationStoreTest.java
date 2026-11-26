package org.puregxl.site.rag.core.memory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.rag.dao.entity.ConversationDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.support.PromptTemplateLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConversationStoreTest {
    private final ConversationMapper mapper = mock(ConversationMapper.class);
    private final LLMService model = mock(LLMService.class);
    private final MemoryProperties properties = new MemoryProperties();
    private final ConversationStore store = new ConversationStore(mapper, model, new PromptTemplateLoader(), properties);

    @Test
    void generatedTitleKeepsWholeUnicodeCharacters() {
        String title = "访问" + "🚲".repeat(30);
        when(model.chat(any(ChatRequest.class))).thenReturn("{\"title\":\"" + title + "\",\"description\":\"简介\"}");
        store.saveConversation("conversation", "user", "周末出行方式");
        ArgumentCaptor<ConversationDO> row = ArgumentCaptor.forClass(ConversationDO.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getTitle()).isEqualTo("访问" + "🚲".repeat(28));
        assertThat(row.getValue().getDescription()).isEqualTo("简介");
    }

    @Test
    void configuredLimitAppliesToPromptAndFallbackWithoutChangingDescription() {
        properties.setTitleMaxLength(10);
        when(model.chat(any(ChatRequest.class))).thenReturn("invalid");
        String question = "骑 " + "🚲".repeat(20);
        store.saveConversation("conversation", "user", question);
        ArgumentCaptor<ChatRequest> call = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model).chat(call.capture());
        assertThat(call.getValue().getMessages().get(0).getContent()).contains("10 个字符以内", question);
        ArgumentCaptor<ConversationDO> row = ArgumentCaptor.forClass(ConversationDO.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getTitle()).isEqualTo("骑" + "🚲".repeat(9));
        assertThat(row.getValue().getDescription()).isEqualTo(question);
    }
}
