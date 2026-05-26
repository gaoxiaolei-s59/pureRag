package org.puregxl.site.rag.core.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class IntentTreeCacheManager {

    private static final String INTENT_CACHE_KEY = "pureragent:intent:tree";
    private static final long CACHE_EXPIRE_DAYS = 7L;
    private static final TypeReference<List<IntentNode>> INTENT_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentTreeCacheManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取意图树缓存。
     * Redis 中没有缓存时返回 null，调用方可据此走数据库或其他回源逻辑。
     */
    public List<IntentNode> getIntentCache() {
        String cacheJson = stringRedisTemplate.opsForValue().get(INTENT_CACHE_KEY);
        if (cacheJson == null || cacheJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(cacheJson, INTENT_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("意图树缓存反序列化失败", ex);
        }
    }

    /**
     * 写入意图树缓存。
     * 空列表不写入，避免用无意义缓存覆盖正常数据。
     */
    public void setIntentCache(List<IntentNode> intentNodes) {
        if (intentNodes == null || intentNodes.isEmpty()) {
            return;
        }

        try {
            String cacheJson = objectMapper.writeValueAsString(intentNodes);
            stringRedisTemplate.opsForValue().set(
                    INTENT_CACHE_KEY,
                    cacheJson,
                    CACHE_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("意图树缓存序列化失败", ex);
        }
    }

    /**
     * 清除意图树缓存。
     * 意图节点发生增删改后调用，保证后续读取能重新回源构建最新树结构。
     */
    public void clear() {
        Boolean deleted = stringRedisTemplate.delete(INTENT_CACHE_KEY);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("意图树缓存已清除, key={}", INTENT_CACHE_KEY);
            return;
        }
        log.info("意图树缓存不存在或无需清除, key={}", INTENT_CACHE_KEY);
    }
}
