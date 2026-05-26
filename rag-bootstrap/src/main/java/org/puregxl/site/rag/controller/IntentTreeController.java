package org.puregxl.site.rag.controller;

import lombok.RequiredArgsConstructor;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.rag.dto.req.IntentNodeCreateRequest;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.rag.service.IntentTreeService;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.util.List;


/**
 * 意图树控制器
 * 提供意图节点树的查询、创建、更新和删除功能
 */
@RestController
@RequiredArgsConstructor
public class IntentTreeController {

    private final IntentTreeService intentTreeService;

//    /**
//     * 查询意向图
//     * @return
//     */
//    @GetMapping("/intent-tree/query")
//    public Result<List<IntentNodeResponse>> queryIntentNode() {
//        return Results.success(intentTreeService.queryIntentNode());
//    }
//
//
//    /**
//     * 创建意向图节点
//     * @param request
//     * @return
//     */
//    @PostMapping("/intent-tree")
//    public Result<Void> createNode(@RequestBody IntentNodeCreateRequest request) {
//
//    }
//
//
//    /**
//     * 删除意向图节点
//     * @return
//     */
//    @DeleteMapping("/intent-tree")
//    public Result<Void> deleteIntentNode() {
//
//    }
//
//
//    /**
//     * 修改意向图节点
//     */
//    @PutMapping("/intent-tree")
//    public Result<Void> updateIntentNode() {
//
//    }


}
