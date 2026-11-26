package org.puregxl.site.rag.core.memory;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.framework.config.DataBaseConfiguration;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.rag.core.usermemory.UserMemoryQueue;
import org.puregxl.site.rag.core.usermemory.UserMemoryExtractor;
import org.puregxl.site.rag.core.usermemory.UserMemoryService;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.dao.mapper.ConversationSummerMapper;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.puregxl.site.rag.support.PromptTemplateLoader;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "RAG_MEMORY_TEST_JDBC_URL", matches = ".+")
class ConversationMemoryMysqlTest {
    private final String user = UUID.randomUUID().toString();
    private final String conversation = UUID.randomUUID().toString();
    private final LLMService llm = mock(LLMService.class);
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final UserMemoryQueue userMemory = mock(UserMemoryQueue.class);
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ConversationMemoryStore store;
    private MemoryMapper mapper;
    private ConversationMapper conversationMapper;
    private final MemoryProperties properties = new MemoryProperties();
    private ConversationSummerStore summaries;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("RAG_MEMORY_TEST_JDBC_URL"),
                System.getenv().getOrDefault("RAG_MEMORY_TEST_USER", "root"),
                System.getenv().getOrDefault("RAG_MEMORY_TEST_PASSWORD", ""));
        new ResourceDatabasePopulator(new ClassPathResource("db/session-memory-fixture.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(ds);
        factoryBean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new DataBaseConfiguration().myMetaObjectHandler()));
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(MemoryMapper.class);
        factory.getConfiguration().addMapper(ConversationMapper.class);
        factory.getConfiguration().addMapper(ConversationSummerMapper.class);
        var session = new SqlSessionTemplate(factory);
        mapper = session.getMapper(MemoryMapper.class);
        conversationMapper = session.getMapper(ConversationMapper.class);
        var redis = mock(RedissonClient.class);
        var lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        properties.setSummaryEnabled(true);
        properties.setHistoryKeepTurns(4);
        summaries = new ConversationSummerStore(session.getMapper(ConversationSummerMapper.class),
                mapper, conversationMapper, properties, llm, new PromptTemplateLoader(), redis, tx, tasks::add);
        store = new ConversationMemoryStore(mapper, conversationMapper, properties, summaries, userMemory);
    }

    @Test
    void reusesTheOverlappingSummaryAndRollsForwardOnceCoverageLeavesTheWindow() {
        when(llm.chat(any(ChatRequest.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "已保存摘要";
        });
        for (int i = 1; i <= 4; i++) {
            saveTurn(i);
            tasks.remove().run();
        }
        verifyNoInteractions(llm);
        saveTurn(5);
        tasks.remove().run();
        assertThat(covered()).isEqualTo(6);
        assertThat(store.loadHistory(conversation, user)).extracting(ChatMessage::getContent)
                .containsExactly("问2", "答2", "问3", "答3", "问4", "答4", "问5", "答5");
        assertThat(summaries.loadSummary(conversation, user).getContent()).contains("已保存摘要");
        assertThat(summaries.loadSummary(conversation, "different-user")).isNull();
        saveTurn(6);
        tasks.remove().run();
        verify(llm).chat(any(ChatRequest.class));
        saveTurn(7);
        tasks.remove().run();
        verify(llm, times(2)).chat(any(ChatRequest.class));
        assertThat(covered()).isEqualTo(10);
    }

    @Test
    void preservesOriginalRowsOnFailureAndRetriesFromThePreviousBoundary() {
        for (int i = 1; i <= 5; i++) {
            saveTurn(i);
        }
        when(llm.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("offline")).thenReturn("恢复");
        tasks.remove().run();
        assertThat(covered()).isZero();
        assertThat(summaries.loadSummary(conversation, user)).isNull();
        tasks.remove().run();
        assertThat(covered()).isEqualTo(6);
        assertThat(summaries.loadSummary(conversation, user).getContent()).contains("恢复");
    }

    @Test
    void failedTurnRollsBackBothMessagesAndDoesNotQueueSummary() {
        doThrow(new IllegalStateException("queue unavailable")).when(userMemory).enqueue(eq(user), anyString());
        assertThatThrownBy(() -> saveTurn(1)).isInstanceOf(IllegalStateException.class);
        assertThat(store.loadHistory(conversation, user)).isEmpty();
        assertThat(tasks).isEmpty();
    }

    @Test
    void discardsModelOutputWhenAnotherWriterReplacedTheSummaryDuringInference() {
        for (int i = 1; i <= 5; i++) {
            saveTurn(i);
        }
        jdbc.update("""
                INSERT INTO t_conversation_summary (id,user_id,conversation_id,summary_content)
                VALUES (?,?,?,'旧摘要')
                """, UUID.randomUUID().toString(), user, conversation);
        when(llm.chat(any(ChatRequest.class))).thenAnswer(call -> {
            jdbc.update("UPDATE t_conversation_summary SET summary_content='已更新' WHERE user_id=?", user);
            return "基于旧版的过时输出";
        });
        tasks.remove().run();
        assertThat(covered()).isZero();
        assertThat(summaries.loadSummary(conversation, user).getContent()).contains("已更新");
    }

    @Test
    void limitsSummaryModelInputAndKeepsTheOriginalMessageInFull() {
        String large = "首段" + "x".repeat(30_000) + "尾段";
        tx.executeWithoutResult(status -> store.saveConversationTurn(conversation, user,
                ChatMessage.user(large), ChatMessage.assistant("回答")));
        for (int i = 2; i <= 5; i++) {
            saveTurn(i);
        }
        when(llm.chat(any(ChatRequest.class))).thenAnswer(call -> {
            ChatRequest request = call.getArgument(0);
            String prompt = request.getMessages().get(1).getContent();
            assertThat(prompt).hasSizeLessThan(25_000).contains("首段", "中段已省略", "尾段");
            return "摘要";
        });
        tasks.remove().run();
        assertThat(covered()).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT content FROM t_memory WHERE user_id=? AND content LIKE '首段%'", String.class, user))
                .isEqualTo(large);
    }

    @Test
    void anotherTransactionHoldingTheConversationRowPreventsAnInterleavedTurn() {
        jdbc.update("INSERT INTO t_conversation (id,user_id,title) VALUES (?,?,?)", conversation, user, "test");
        var competing = new TransactionTemplate(tx.getTransactionManager());
        competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(outer -> {
            jdbc.queryForObject("SELECT id FROM t_conversation WHERE id=? FOR UPDATE", String.class, conversation);
            assertThatThrownBy(() -> competing.executeWithoutResult(inner -> {
                jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
                store.saveConversationTurn(conversation, user, ChatMessage.user("Q"), ChatMessage.assistant("A"));
            })).isInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class);
        });
        assertThat(store.loadHistory(conversation, user)).isEmpty();
    }

    @Test
    void refusesDeletedConversationsAndIdsBelongingToAnotherUser() {
        jdbc.update("INSERT INTO t_conversation (id,user_id,title) VALUES (?,?,?)", conversation, "another-user", "original");
        assertThatThrownBy(() -> saveTurn(1)).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE t_conversation SET user_id=?,del_flag=1 WHERE id=?", user, conversation);
        assertThatThrownBy(() -> saveTurn(1)).isInstanceOf(IllegalStateException.class);
        assertThat(store.loadHistory(conversation, user)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT del_flag FROM t_conversation WHERE id=?", Integer.class, conversation)).isOne();
    }

    @Test
    void aCommittedTurnCanTeachTheSameUserAcrossConversations() {
        new ResourceDatabasePopulator(new ClassPathResource("db/manual/20261125_user_memory.sql")).execute(jdbc.getDataSource());
        UserMemoryQueue queue = new UserMemoryQueue(jdbc, true);
        store = new ConversationMemoryStore(mapper, conversationMapper, properties, summaries, queue);
        String question = "我习惯看简短回答";
        tx.executeWithoutResult(status -> store.saveConversationTurn(conversation, user, ChatMessage.user(question),
                ChatMessage.assistant("收到")));
        when(llm.chat(any(ChatRequest.class))).thenAnswer(call -> {
            ChatRequest request = call.getArgument(0);
            assertThat(request.getMessages().get(1).getContent()).contains(question).doesNotContain("收到");
            return """
                    {"edits":[{"op":"add","quote":"我习惯看简短回答","fact":"用户偏好简短回答"}]}
                    """;
        });
        var profiles = new UserMemoryService(queue, new UserMemoryExtractor(llm, new PromptTemplateLoader()), Runnable::run);
        profiles.learn(user);
        assertThat(store.loadHistory("new-conversation", user)).isEmpty();
        assertThat(profiles.load(user).getContent()).contains("用户偏好简短回答");
        assertThat(profiles.load("another-user")).isNull();
        verify(llm).chat(any(ChatRequest.class));
    }

    private int covered() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_memory WHERE user_id=? AND conversation_id=? AND compressed_flag=1",
                Integer.class, user, conversation);
    }

    private void saveTurn(int turn) {
        tx.executeWithoutResult(status -> {
            int queued = tasks.size();
            store.saveConversationTurn(conversation, user, ChatMessage.user("问" + turn), ChatMessage.assistant("答" + turn));
            assertThat(tasks).hasSize(queued);
        });
    }
}
