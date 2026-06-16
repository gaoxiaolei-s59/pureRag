package org.puregxl.site.rag.controller;

import org.junit.jupiter.api.Test;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.rag.dto.req.RagPipelineEvalRequest;
import org.puregxl.site.rag.dto.resp.RagPipelineEvalResponse;
import org.puregxl.site.rag.service.RagEvalService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvalControllerTest {

    @Test
    void evaluatePipelineDelegatesToService() {
        RagEvalService ragEvalService = mock(RagEvalService.class);
        RagEvalController controller = new RagEvalController(ragEvalService);
        RagPipelineEvalRequest request = new RagPipelineEvalRequest();
        request.setUserQuestion("报销流程在哪申请");
        request.setConversationId("conv-1");
        request.setDeepThinking(Boolean.FALSE);

        RagPipelineEvalResponse response = RagPipelineEvalResponse.builder()
                .userQuestion("报销流程在哪申请")
                .predictedIntentIds(List.of("intent-finance"))
                .allSystemOnly(false)
                .build();
        when(ragEvalService.evaluatePipeline(request)).thenReturn(response);

        Result<RagPipelineEvalResponse> result = controller.evaluatePipeline(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getPredictedIntentIds()).containsExactly("intent-finance");
        verify(ragEvalService).evaluatePipeline(request);
    }
}
