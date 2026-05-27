package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.memory.ConversationMemoryService;
import org.puregxl.site.rag.core.memory.ConversationStore;
import org.puregxl.site.rag.core.memory.ConversationSummerService;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.puregxl.site.rag.dto.resp.MemoryQueryResponse;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryServiceImplTest {

    @AfterEach
    void tearDown() {
        UserContext.removeUserContext();
    }

    @Test
    void queryAllChatMessageLoadsCurrentUserConversationAndMapsResponse() {
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        ConversationSummerService conversationSummerService = mock(ConversationSummerService.class);
        Executor executor = Runnable::run;
        ConversationStore conversationStore = mock(ConversationStore.class);
        MemoryMapper memoryMapper = mock(MemoryMapper.class);
        MemoryServiceImpl service = new MemoryServiceImpl(
                conversationMemoryService,
                conversationSummerService,
                executor,
                conversationStore,
                memoryMapper
        );
        UserContext.setUserContext(UserInfoDTO.builder().userId("user-1").build());
        when(memoryMapper.selectList(any())).thenReturn(List.of(
                MemoryDO.builder().conversationId("conv-1").UserId("user-1").role("user").content("你好").build(),
                MemoryDO.builder().conversationId("conv-1").UserId("user-1").role("assistant").content("您好，有什么可以帮您？").build()
        ));

        List<MemoryQueryResponse> result = service.queryAllChatMessage("conv-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo(ChatMessage.Role.USER.name().toLowerCase());
        assertThat(result.get(0).getContent()).isEqualTo("你好");
        assertThat(result.get(1).getRole()).isEqualTo(ChatMessage.Role.ASSISTANT.name().toLowerCase());
        assertThat(result.get(1).getContent()).isEqualTo("您好，有什么可以帮您？");

        verify(memoryMapper).selectList(any());
    }
}
