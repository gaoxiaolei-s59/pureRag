package org.puregxl.site.rag.core.rewrite;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.support.PromptTemplateLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutiQueryRewriteServiceTest {

    @Test
    void rewriteParsesModelJsonResponse() {
        LLMService llmService = mock(LLMService.class);
        MutiQueryRewriteService service = new MutiQueryRewriteService(llmService, new PromptTemplateLoader());
        List<ChatMessage> history = List.of(
                ChatMessage.user("它支持哪些格式？"),
                ChatMessage.assistant("支持 PDF 和 DOCX。")
        );

        when(llmService.chat(anyString())).thenReturn("""
                {
                  "rewrittenQuestion": "这个系统支持哪些文件格式？",
                  "subQuestions": ["支持哪些文档格式", "是否支持 PDF", "是否支持 DOCX"]
                }
                """);

        RewriteResult result = service.rewrite("那它还支持什么？", history);

        assertThat(result.getRewrittenQuestion()).isEqualTo("这个系统支持哪些文件格式？");
        assertThat(result.getSubQuestions()).containsExactly("支持哪些文档格式", "是否支持 PDF", "是否支持 DOCX");
        verify(llmService).chat(anyString());
    }

    @Test
    void rewriteFallsBackToOriginalQuestionWhenModelResponseCannotBeParsed() {
        LLMService llmService = mock(LLMService.class);
        MutiQueryRewriteService service = new MutiQueryRewriteService(llmService, new PromptTemplateLoader());

        when(llmService.chat(anyString())).thenReturn("我觉得可以问得更明确一点");

        RewriteResult result = service.rewrite("那它多久过期？", List.of());

        assertThat(result.getRewrittenQuestion()).isEqualTo("那它多久过期？");
        assertThat(result.getSubQuestions()).isEmpty();
    }

    @Test
    void rewriteRequestBuilderIncludesHistoryAndCurrentQuestionFromPromptTemplate() {
        LLMService llmService = mock(LLMService.class);
        MutiQueryRewriteService service = new MutiQueryRewriteService(llmService, new PromptTemplateLoader());

        String prompt = service.rewriteRequestBuilder(
                "那它还支持什么？",
                List.of(
                        ChatMessage.user("它支持哪些格式？"),
                        ChatMessage.assistant("支持 PDF 和 DOCX。")
                )
        );

        assertThat(prompt)
                .contains("历史对话")
                .contains("user: 它支持哪些格式？")
                .contains("assistant: 支持 PDF 和 DOCX。")
                .contains("当前问题：")
                .contains("那它还支持什么？");
    }
}
