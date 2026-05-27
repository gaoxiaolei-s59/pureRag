package org.puregxl.site.rag.controller;

import lombok.RequiredArgsConstructor;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.dto.resp.MemoryQueryResponse;
import org.puregxl.site.rag.service.MemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 */
@RestController
@RequiredArgsConstructor
public class MemoryController {
    private final MemoryService memoryService;




    @GetMapping("/memory/v1/query")
    public Result<List<MemoryQueryResponse>> queryAllChatMessage(String conversionId) {
        List<MemoryQueryResponse> memoryList =  memoryService.queryAllChatMessage(conversionId);
        return Results.success(memoryList);
    }

}
