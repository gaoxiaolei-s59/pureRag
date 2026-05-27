package org.puregxl.site.rag.controller;

import org.junit.jupiter.api.Test;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.rag.dto.req.IntentNodeCreateRequest;
import org.puregxl.site.rag.dto.req.IntentNodeUpdateRequest;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.rag.service.IntentTreeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentTreeControllerTest {

    @Test
    void createNodeDelegatesToService() {
        IntentTreeService intentTreeService = mock(IntentTreeService.class);
        IntentTreeController controller = new IntentTreeController(intentTreeService);
        IntentNodeCreateRequest request = IntentNodeCreateRequest.builder().intentCode("intent-a").name("意图A").build();

        Result<Void> result = controller.createNode(request);

        assertThat(result.isSuccess()).isTrue();
        verify(intentTreeService).createIntentNode(request);
    }

    @Test
    void getIntentNodeByIdDelegatesToService() {
        IntentTreeService intentTreeService = mock(IntentTreeService.class);
        IntentTreeController controller = new IntentTreeController(intentTreeService);
        IntentNodeResponse response = new IntentNodeResponse();
        response.setRecordId("db-1");
        when(intentTreeService.getIntentNodeById("db-1")).thenReturn(response);

        Result<IntentNodeResponse> result = controller.getIntentNodeById("db-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecordId()).isEqualTo("db-1");
        verify(intentTreeService).getIntentNodeById("db-1");
    }

    @Test
    void deleteIntentNodeDelegatesToService() {
        IntentTreeService intentTreeService = mock(IntentTreeService.class);
        IntentTreeController controller = new IntentTreeController(intentTreeService);

        Result<Void> result = controller.deleteIntentNode("id-1");

        assertThat(result.isSuccess()).isTrue();
        verify(intentTreeService).deleteIntentNode("id-1");
    }

    @Test
    void updateIntentNodeDelegatesToService() {
        IntentTreeService intentTreeService = mock(IntentTreeService.class);
        IntentTreeController controller = new IntentTreeController(intentTreeService);
        IntentNodeUpdateRequest request = IntentNodeUpdateRequest.builder().intentCode("intent-b").name("意图B").build();

        Result<Void> result = controller.updateIntentNode("id-2", request);

        assertThat(result.isSuccess()).isTrue();
        verify(intentTreeService).updateIntentNode("id-2", request);
    }
}
