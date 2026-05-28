package org.puregxl.site.rag.core.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.puregxl.site.rag.enums.IntentKind;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentTreeCacheManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getIntentCacheDeserializesIntentNodeListFromRedisJson() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        IntentTreeCacheManager cacheManager = new IntentTreeCacheManager(stringRedisTemplate);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pureragent:intent:tree")).thenReturn("""
                [
                  {
                    "recordId": "db-1",
                    "id": "logistics-overseas-customs",
                    "kbId": "1997857139737882625",
                    "name": "清关流程",
                    "description": "跨境物流的清关申报、关税计算、禁运品规则等相关说明",
                    "level": "TOPIC",
                    "parentId": "logistics-overseas",
                    "examples": ["海淘包裹清关一般要多久？"],
                    "children": [],
                    "fullPath": "跨境物流 > 清关流程",
                    "kind": "KB",
                    "collectionName": "kb_1997857139737882625",
                    "topK": 5,
                    "promptSnippet": "优先回答清关问题"
                  }
                ]
                """);

        List<IntentNode> result = cacheManager.getIntentCache();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("logistics-overseas-customs");
        assertThat(result.get(0).getExamples()).containsExactly("海淘包裹清关一般要多久？");
        assertThat(result.get(0).getKind()).isNotNull();
    }

    @Test
    void serializeIntentNodeDoesNotExposeDerivedBooleanHelpers() throws JsonProcessingException {
        IntentNode intentNode = IntentNode.builder()
                .id("group-hr")
                .name("人事服务")
                .kind(IntentKind.KB)
                .build();

        String json = objectMapper.writeValueAsString(intentNode);

        assertThat(json).doesNotContain("\"kb\"");
        assertThat(json).doesNotContain("\"mcp\"");
        assertThat(json).doesNotContain("\"system\"");
        assertThat(json).doesNotContain("\"leaf\"");
    }
}
