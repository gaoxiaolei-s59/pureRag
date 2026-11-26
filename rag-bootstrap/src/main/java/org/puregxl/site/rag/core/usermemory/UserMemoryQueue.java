package org.puregxl.site.rag.core.usermemory;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 长期记忆与其待学消息。每次模型调用在事务外进行，提交时才比较版本。 */
@Repository
public class UserMemoryQueue {
    private static final int MAX_FAILURES = 5;
    private static final Gson JSON = new Gson();
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public UserMemoryQueue(JdbcTemplate jdbc, @Value("${rag.memory.long-term-enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    /**
     * 由完整对话的落库事务调用。先锁定用户行，再分配任务序号，同一用户不同会话的并发
     * 写入因此按提交顺序可见。源消息与待学任务一起提交或回滚。
     */
    public void enqueue(String userId, String sourceMessageId) {
        if (!enabled || userId == null || userId.isBlank()) {
            return;
        }
        jdbc.update("""
                INSERT INTO t_user_memory_profile (user_id, facts) VALUES (?, JSON_ARRAY())
                ON DUPLICATE KEY UPDATE version=version
                """, userId);
        jdbc.update("INSERT INTO t_user_memory_job (user_id, source_message_id) VALUES (?, ?)", userId, sourceMessageId);
    }

    List<String> facts(String userId) {
        return jdbc.query("SELECT facts FROM t_user_memory_profile WHERE user_id=?",
                (rs, index) -> decode(rs.getString(1)), userId).stream().findFirst().orElse(List.of());
    }

    /** 一条查询同时读任务、用户快照及版本，重试中的队首阻止后续任务提前覆盖偏好。 */
    Job next(String userId) {
        return jdbc.query("""
                SELECT j.id, j.failure_count, p.version, p.facts, LEFT(m.content, ?) AS content
                FROM t_user_memory_job j
                JOIN t_user_memory_profile p ON p.user_id=j.user_id
                LEFT JOIN t_memory m ON m.id=j.source_message_id AND m.user_id=j.user_id
                                     AND m.role='user' AND m.del_flag=0
                WHERE j.user_id=? AND j.retry_at<=CURRENT_TIMESTAMP(6)
                  AND j.id=(SELECT MIN(head.id) FROM t_user_memory_job head
                            WHERE head.user_id=? AND head.failure_count<?)
                """, (rs, index) -> new Job(rs.getLong("id"), rs.getLong("version"), rs.getInt("failure_count"),
                        decode(rs.getString("facts")), rs.getString("content")),
                UserMemoryExtractor.MAX_INPUT_CHARS + 1, userId, userId, MAX_FAILURES).stream().findFirst().orElse(null);
    }

    /** 将新快照和任务确认放在同一个事务。版本冲突表示另一实例已处理该任务。 */
    @Transactional
    public boolean complete(String userId, Job job, List<String> facts) {
        int changed = jdbc.update("""
                UPDATE t_user_memory_profile SET facts=?, version=version+1 WHERE user_id=? AND version=?
                """, JSON.toJson(facts), userId, job.version());
        if (changed == 0) {
            return false;
        }
        if (jdbc.update("DELETE FROM t_user_memory_job WHERE user_id=? AND id=? AND failure_count<?",
                userId, job.id(), MAX_FAILURES) != 1) {
            throw new IllegalStateException("长期记忆版本与待学任务不一致");
        }
        return true;
    }

    /** 相同轮次的并行失败只计一次。连续五次失败后保留任务供排查，后续更正或遗忘仍可执行。 */
    boolean defer(String userId, Job job) {
        int changed = jdbc.update("""
                UPDATE t_user_memory_job SET retry_at=TIMESTAMPADD(SECOND, 30 * (failure_count + 1), CURRENT_TIMESTAMP(6)),
                    failure_count=failure_count + 1
                WHERE user_id=? AND id=? AND failure_count=? AND failure_count<?
                """, userId, job.id(), job.failures(), MAX_FAILURES);
        return changed == 1 && job.failures() + 1 == MAX_FAILURES;
    }

    /** 优先恢复最早且到期的队首任务，重试等待中的用户不占本轮名额。 */
    List<String> readyUsers() {
        return jdbc.queryForList("""
                SELECT j.user_id FROM t_user_memory_job j
                WHERE j.failure_count<? AND j.retry_at<=CURRENT_TIMESTAMP(6)
                  AND NOT EXISTS (SELECT 1 FROM t_user_memory_job prev WHERE prev.user_id=j.user_id
                                  AND prev.id<j.id AND prev.failure_count<?)
                ORDER BY j.id LIMIT 64
                """, String.class, MAX_FAILURES, MAX_FAILURES);
    }

    private static List<String> decode(String json) {
        return List.of(JSON.fromJson(json, String[].class));
    }

    record Job(long id, long version, int failures, List<String> facts, String content) {}
}
