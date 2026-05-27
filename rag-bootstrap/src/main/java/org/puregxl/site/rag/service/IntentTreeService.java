package org.puregxl.site.rag.service;

import org.puregxl.site.rag.dto.req.IntentNodeCreateRequest;
import org.puregxl.site.rag.dto.req.IntentNodeUpdateRequest;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;

import java.util.List;

public interface IntentTreeService {
    List<IntentNodeResponse> queryIntentNode();

    IntentNodeResponse getIntentNodeById(String id);

    void createIntentNode(IntentNodeCreateRequest request);

    void updateIntentNode(String id, IntentNodeUpdateRequest request);

    void deleteIntentNode(String id);
}
