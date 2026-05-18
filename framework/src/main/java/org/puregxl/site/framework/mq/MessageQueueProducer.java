package org.puregxl.site.framework.mq;

import org.apache.rocketmq.client.producer.SendResult;

import java.util.function.Consumer;

public interface MessageQueueProducer {

    /**
     * 发送普通消息
     * @param topic
     * @param keys
     * @param object
     * @return
     */
    SendResult send(String topic, String keys, Object object);


    /**
     * 发送事务消息
     * @param topic
     * @param keys
     * @param object
     * @param localTransaction
     */
    void sendInTransaction(String topic, String keys, Object object, Consumer<Object> localTransaction);
}
