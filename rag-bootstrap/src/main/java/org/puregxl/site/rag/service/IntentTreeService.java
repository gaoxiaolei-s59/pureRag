package org.puregxl.site.rag.service;

import org.puregxl.site.rag.dto.resp.IntentNodeResponse;

import java.util.List;

public interface IntentTreeService {
    List<IntentNodeResponse> queryIntentNode();
}
