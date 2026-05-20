package org.puregxl.site.bootstrap.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * redission 锁
 */
@Component
@RequiredArgsConstructor
public class RedissonLockCreator {
    private final RedissonClient redissonClient;

    /**
     * 获取普通锁
     * @param lockKey
     * @return
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }


    /**
     * 释放锁
     * @param lockKey
     */
    public void unLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
