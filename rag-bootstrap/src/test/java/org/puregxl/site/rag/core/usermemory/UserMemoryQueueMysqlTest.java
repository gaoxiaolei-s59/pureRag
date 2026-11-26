package org.puregxl.site.rag.core.usermemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅面向独立的 MySQL 测试库。运行时传入 RAG_MEMORY_TEST_JDBC_URL。 */
@EnabledIfEnvironmentVariable(named = "RAG_MEMORY_TEST_JDBC_URL", matches = ".+")
class UserMemoryQueueMysqlTest {
    private final String user = UUID.randomUUID().toString();
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private UserMemoryQueue queue;

    @BeforeEach
    void initialize() {
        var dataSource = new DriverManagerDataSource(System.getenv("RAG_MEMORY_TEST_JDBC_URL"),
                System.getenv().getOrDefault("RAG_MEMORY_TEST_USER", "root"),
                System.getenv().getOrDefault("RAG_MEMORY_TEST_PASSWORD", ""));
        new ResourceDatabasePopulator(new ClassPathResource("db/manual/20261125_user_memory.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/session-memory-fixture.sql")).execute(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        queue = new UserMemoryQueue(jdbc, true);
    }

    @AfterEach
    void removeTestAccount() {
        jdbc.update("DELETE FROM t_user_memory_job WHERE user_id=?", user);
        jdbc.update("DELETE FROM t_user_memory_profile WHERE user_id=?", user);
        jdbc.update("DELETE FROM t_memory WHERE user_id=?", user);
    }

    @Test
    void rollbackCancelsQueueAndConflictingWorkersCannotOverwriteNewerFacts() {
        tx.executeWithoutResult(status -> {
            saveUserMessage("rolled back");
            status.setRollbackOnly();
        });
        assertNull(queue.next(user));
        saveUserMessage("old preference");
        saveUserMessage("new preference");
        var first = queue.next(user);
        assertEquals("old preference", first.content());
        assertTrue(tx.execute(status -> queue.complete(user, first, List.of("old"))).booleanValue());
        assertFalse(tx.execute(status -> queue.complete(user, first, List.of("stale"))).booleanValue());
        var second = queue.next(user);
        assertEquals(List.of("old"), second.facts());
        assertEquals("new preference", second.content());
        assertTrue(tx.execute(status -> queue.complete(user, second, List.of("new"))).booleanValue());
        assertNull(queue.next(user));
        assertEquals(List.of("new"), queue.facts(user));
        assertEquals(List.of(), queue.facts(UUID.randomUUID().toString()));
    }

    @Test
    void workerFailureDefersOnlyThisUserAndNextWakeCanReplayAfterRestart() {
        var extractor = mock(UserMemoryExtractor.class);
        var service = new UserMemoryService(queue, extractor, Runnable::run);
        saveUserMessage("prefer concise replies");
        when(extractor.extract(List.of(), "prefer concise replies")).thenThrow(new IllegalStateException("offline"));
        service.learn(user);
        assertNull(service.load(user));
        assertNull(queue.next(user));
        jdbc.update("UPDATE t_user_memory_job SET retry_at=CURRENT_TIMESTAMP WHERE user_id=?", user);
        doReturn(List.of("prefers concise replies")).when(extractor).extract(List.of(), "prefer concise replies");
        new UserMemoryService(queue, extractor, Runnable::run).recover();
        assertTrue(service.load(user).getContent().contains("prefers concise replies"));
        assertEquals(ChatMessage.Role.USER, service.load(user).getRole());
    }

    @Test
    void permanentlyFailedMessageDoesNotBlockLaterForgetRequest() {
        saveUserMessage("remember");
        var remember = queue.next(user);
        tx.executeWithoutResult(status -> assertTrue(queue.complete(user, remember, List.of("old"))));
        saveUserMessage("invalid response");
        saveUserMessage("forget");
        for (int attempt = 0; attempt < 5; attempt++) {
            var head = queue.next(user);
            assertEquals("invalid response", head.content());
            queue.defer(user, head);
            queue.defer(user, head);
            jdbc.update("UPDATE t_user_memory_job SET retry_at=CURRENT_TIMESTAMP WHERE id=?", head.id());
        }
        assertTrue(queue.readyUsers().contains(user));
        var later = queue.next(user);
        assertEquals("forget", later.content());
        tx.executeWithoutResult(status -> assertTrue(queue.complete(user, later, List.of())));
        assertNull(queue.next(user));
        assertFalse(queue.readyUsers().contains(user));
        assertEquals(List.of(), queue.facts(user));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_user_memory_job WHERE user_id=? AND failure_count=5",
                Integer.class, user));
    }

    private void saveUserMessage(String body) {
        tx.executeWithoutResult(status -> {
            String message = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO t_memory (id,user_id,role,content) VALUES (?,?,'user',?)", message, user, body);
            queue.enqueue(user, message);
        });
    }
}
