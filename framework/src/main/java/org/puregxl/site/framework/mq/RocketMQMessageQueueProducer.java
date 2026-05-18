package org.puregxl.site.framework.mq;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.puregxl.site.framework.exception.ClientException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class RocketMQMessageQueueProducer implements MessageQueueProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public SendResult send(String topic, String keys, Object object) {
        Message<Object> message = buildMessage(keys, object);
        return rocketMQTemplate.syncSend(topic, message);
    }

    @Override
    public void sendInTransaction(String topic, String keys, Object object, Consumer<Object> localTransaction) {
        if (localTransaction == null) {
            throw new ClientException("本地事务不能为空");
        }
        Message<Object> message = buildMessage(keys, object);
        rocketMQTemplate.sendMessageInTransaction(topic, message, localTransaction);
    }

    private Message<Object> buildMessage(String keys, Object object) {
        if (object == null) {
            throw new ClientException("消息体不能为空");
        }
        MessageBuilder<Object> builder = MessageBuilder.withPayload(object);
        if (StringUtils.hasText(keys)) {
            builder.setHeader(RocketMQHeaders.KEYS, keys);
        }
        return builder.build();
    }
}
