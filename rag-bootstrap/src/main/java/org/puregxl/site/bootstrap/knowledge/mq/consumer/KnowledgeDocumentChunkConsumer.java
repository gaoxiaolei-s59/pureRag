package org.puregxl.site.bootstrap.knowledge.mq.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.RocketMQConsumerLifecycleListener;
import org.puregxl.site.bootstrap.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.bootstrap.user.context.UserInfoDTO;
import org.puregxl.site.framework.mq.MessageWrapper;
import org.springframework.stereotype.Component;


/**
 * 文档分块任务 MQ 消费者
 * 负责异步执行耗时的文本提取、分块、向量嵌入及写库操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "knowledge-document-chunk_topic${unique-name:}",
        consumerGroup = "knowledge-document-chunk_cg${unique-name:}"
)
public class KnowledgeDocumentChunkConsumer implements RocketMQListener<MessageWrapper<KnowledgeDocumentChunkEvent>> {

    private final KnowledgeDocumentService documentService;

    @Override
    public void onMessage(MessageWrapper<KnowledgeDocumentChunkEvent> message) {
        KnowledgeDocumentChunkEvent event = message.getBody();

        log.info("[消费者] 开始消费文档分块任务，docId={}, keys={}", event.getDocId(), message.getKeys());

        UserContext.setUserContext(UserInfoDTO.builder().userId(event.getOperator()).build());
        try {
            documentService.executeChunk(event.getDocId());
        } finally {
            UserContext.removeUserContext();
        }
    }
}
