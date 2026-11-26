package org.puregxl.site.rag.core.memory;

import org.junit.jupiter.api.Test;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.usermemory.UserMemoryQueue;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationMemoryStoreTest {
    @Test
    void skipsOrphanRepliesAndInvalidLegacyRowsWithoutDroppingTheRestOfTheWindow() {
        MemoryMapper mapper = mock(MemoryMapper.class);
        ConversationMemoryStore store = new ConversationMemoryStore(mapper, mock(ConversationMapper.class),
                new MemoryProperties(), mock(ConversationSummerService.class), mock(UserMemoryQueue.class));
        when(mapper.selectList(any())).thenReturn(List.of(
                message("assistant", "answer"), message("other", "ignored"), message("user", "question"),
                message("assistant", "orphan"), message("user", "   ")));

        assertThat(store.loadHistory("conversation", "user"))
                .containsExactly(ChatMessage.user("question"), ChatMessage.assistant("answer"));
    }

    private static MemoryDO message(String role, String content) {
        return MemoryDO.builder().role(role).content(content).build();
    }
}
