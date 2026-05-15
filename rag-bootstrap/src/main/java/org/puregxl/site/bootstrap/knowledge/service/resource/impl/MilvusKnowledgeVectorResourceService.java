package org.puregxl.site.bootstrap.knowledge.service.resource.impl;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.bootstrap.config.RagVectorProperties;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.framework.exception.ServiceException;
import org.puregxl.site.framework.exception.kb.VectorCollectionAlreadyExistsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Milvus 向量资源服务，封装 Collection、字段、索引和加载流程。
 */
@Service
@RequiredArgsConstructor
public class MilvusKnowledgeVectorResourceService implements KnowledgeVectorResourceService {

    private static final Pattern MILVUS_COLLECTION_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,254}$");
    private static final String DOC_ID_FIELD = "doc_id";
    private static final String CONTENT_FIELD = "content";
    private static final String METADATA_FIELD = "metadata";
    private static final String EMBEDDING_FIELD = "embedding";

    private final RagVectorProperties ragVectorProperties;
    private final MilvusClientV2 milvusClient;

    @Override
    public String normalizeCollectionName(String collectionName) {
        String normalized = collectionName.trim();
        if (!MILVUS_COLLECTION_NAME_PATTERN.matcher(normalized).matches()) {
            throw new ServiceException("Milvus Collection 名称只能包含字母、数字、下划线，且必须以字母或下划线开头，长度不超过 255");
        }
        return normalized;
    }

    @Override
    public void createCollection(String collectionName) {
        RagVectorProperties.MilvusConfig milvusConfig = ragVectorProperties.getMilvus();
        try {
            Boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            if (Boolean.TRUE.equals(exists)) {
                throw new VectorCollectionAlreadyExistsException(collectionName);
            }

            // 知识库 Collection 固定字段：文档 ID、原文、元数据、embedding 向量。
            CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName(DOC_ID_FIELD)
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .maxLength(64)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(CONTENT_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(METADATA_FIELD)
                    .dataType(DataType.JSON)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(EMBEDDING_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(milvusConfig.getDimension())
                    .build());

            milvusClient.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .build());

            milvusClient.createIndex(CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(List.of(IndexParam.builder()
                            .fieldName(EMBEDDING_FIELD)
                            .indexType(IndexParam.IndexType.AUTOINDEX)
                            .metricType(toMetricType(milvusConfig.getMetricType()))
                            .build()))
                    .build());

            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (VectorCollectionAlreadyExistsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("创建 Milvus 向量集合失败：" + ex.getMessage());
        }
    }

    @Override
    public void rollbackCollection(String collectionName) {
        try {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception ignored) {
            // 创建流程失败后的兜底清理，清理失败不覆盖原始异常。
        }


    }


    private IndexParam.MetricType toMetricType(String metricType) {
        try {
            return IndexParam.MetricType.valueOf(metricType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ServiceException("不支持的 Milvus metricType：" + metricType);
        }
    }
}
