package org.puregxl.site.bootstrap.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeChunkDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkBatchRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkCreateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkUpdateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.response.KnowledgeChunkResponse;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeChunkService;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkResponse createKnowledgeChunk(String docId, KnowledgeChunkCreateRequest request) {
        KnowledgeDocumentDO document = getDocument(docId);
        if (request == null || StrUtil.isBlank(request.getContent())) {
            throw new ClientException("分块内容不能为空");
        }

        KnowledgeChunkDO chunk = KnowledgeChunkDO.builder()
                .kbId(document.getKbId())
                .docId(docId)
                .chunkIndex(request.getIndex())
                .content(request.getContent())
                .contentHash(Integer.toHexString(request.getContent().hashCode()))
                .charCount(request.getContent().length())
                .enabled(1)
                .createdBy(currentUserId())
                .deleted(0)
                .build();
        int inserted = knowledgeChunkMapper.insert(chunk);
        if (inserted != 1) {
            throw new ServiceException("新增 Chunk 失败");
        }
        refreshDocumentChunkCount(docId);
        return BeanUtil.toBean(chunk, KnowledgeChunkResponse.class);
    }

    @Override
    public void updateKnowledgeChunk(String docId, String chunkId, KnowledgeChunkUpdateRequest request) {
        KnowledgeChunkDO chunk = getChunk(docId, chunkId);
        if (request == null || StrUtil.isBlank(request.getContent())) {
            throw new ClientException("分块内容不能为空");
        }

        KnowledgeChunkDO update = KnowledgeChunkDO.builder()
                .id(chunk.getId())
                .content(request.getContent())
                .contentHash(Integer.toHexString(request.getContent().hashCode()))
                .charCount(request.getContent().length())
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeChunkMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("更新 Chunk 失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String docId, String chunkId) {
        KnowledgeChunkDO chunk = getChunk(docId, chunkId);
        KnowledgeChunkDO update = KnowledgeChunkDO.builder()
                .id(chunk.getId())
                .updatedBy(currentUserId())
                .deleted(1)
                .build();
        int updated = knowledgeChunkMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("删除 Chunk 失败");
        }
        refreshDocumentChunkCount(docId);
    }

    @Override
    public void enableChunk(String docId, String chunkId, boolean enabled) {
        KnowledgeChunkDO chunk = getChunk(docId, chunkId);
        KnowledgeChunkDO update = KnowledgeChunkDO.builder()
                .id(chunk.getId())
                .enabled(enabled ? 1 : 0)
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeChunkMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("更新 Chunk 启用状态失败");
        }
    }

    @Override
    public void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest request, boolean enabled) {
        getDocument(docId);
        knowledgeChunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getDocId, docId)
                .in(request != null && request.getChunkIds() != null && !request.getChunkIds().isEmpty(),
                        KnowledgeChunkDO::getId,
                        request == null ? null : request.getChunkIds())
                .set(KnowledgeChunkDO::getEnabled, enabled ? 1 : 0)
                .set(KnowledgeChunkDO::getUpdatedBy, currentUserId()));
    }

    private KnowledgeDocumentDO getDocument(String docId) {
        if (StrUtil.isBlank(docId)) {
            throw new ClientException("文档 ID 不能为空");
        }
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null) {
            throw new ClientException("文档不存在");
        }
        return document;
    }

    private KnowledgeChunkDO getChunk(String docId, String chunkId) {
        if (StrUtil.isBlank(chunkId)) {
            throw new ClientException("Chunk ID 不能为空");
        }
        KnowledgeChunkDO chunk = knowledgeChunkMapper.selectOne(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getId, chunkId)
                .eq(KnowledgeChunkDO::getDocId, docId));
        if (chunk == null) {
            throw new ClientException("Chunk 不存在");
        }
        return chunk;
    }

    private void refreshDocumentChunkCount(String docId) {
        Long count = knowledgeChunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getDocId, docId));
        knowledgeDocumentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getId, docId)
                .set(KnowledgeDocumentDO::getChunkCount, count == null ? 0 : count.intValue())
                .set(KnowledgeDocumentDO::getUpdatedBy, currentUserId()));
    }

    private String currentUserId() {
        return UserContext.getUserContext() == null ? null : UserContext.getUserContext().getUserId();
    }
}
