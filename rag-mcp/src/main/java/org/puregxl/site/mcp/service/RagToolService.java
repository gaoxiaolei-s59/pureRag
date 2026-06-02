package org.puregxl.site.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.puregxl.site.mcp.dao.entity.ResumeEntity;
import org.puregxl.site.mcp.dao.mapper.ResumeMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RagToolService {

    private final ResumeMapper resumeMapper;


    public RagToolService(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }

    private static final List<KnowledgeDocument> KNOWLEDGE_BASE = List.of(
            new KnowledgeDocument(
                    "exam_defer_policy",
                    "缓考申请",
                    "缓考申请：因生病或重大突发情况无法参加考试的学生，应在考试前提交缓考申请，并附相关证明材料。"
            ),
            new KnowledgeDocument(
                    "exam_identity_policy",
                    "考试证件要求",
                    "考试证件要求：参加考试时，学生须携带本人学生证或校园卡。"
            ),
            new KnowledgeDocument(
                    "exam_makeup_policy",
                    "补考规定",
                    "补考规定：期末考试不及格的学生可参加下一学期开学初组织的补考。"
            ),
            new KnowledgeDocument(
                    "course_selection_policy",
                    "选课规则",
                    "选课规则：学生应在每学期开学前两周内登录教务系统完成选课。"
            ),
            new KnowledgeDocument(
                    "course_withdraw_policy",
                    "退课规则",
                    "退课规则：学生在开课后第一周内可申请退课，超过时间需审批。"
            ),
            new KnowledgeDocument(
                    "course_capacity_policy",
                    "课程容量说明",
                    "课程容量说明：热门课程达到人数上限后系统不再接受新的选课请求。"
            )
    );

    @Tool(name = "rag_ping", description = "检查 RAG MCP 服务是否可用")
    public String ping() {
        return "rag-mcp is running";
    }

    @Tool(name = "rag_search", description = "从 RAG 知识库中检索与 query 最相关的片段，并返回 fileId 方便追溯来源文件")
    public List<SearchResult> search(
            @ToolParam(description = "检索问题或关键词") String query,
            @ToolParam(description = "最多返回多少条结果", required = false) Integer topK) {

        int limit = normalizeTopK(topK);
        List<String> queryTerms = tokenize(query);

        return KNOWLEDGE_BASE.stream()
                .map(document -> toSearchResult(document, queryTerms))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    @Tool(name = "rag_answer", description = "基于 RAG 知识库生成简短答案，并附带 fileId 引用")
    public RagAnswer answer(
            @ToolParam(description = "用户问题") String question,
            @ToolParam(description = "最多使用多少条检索结果", required = false) Integer topK) {

        List<SearchResult> contexts = search(question, topK);
        if (contexts.isEmpty()) {
            return new RagAnswer("知识库中没有找到相关内容。", List.of());
        }

        StringBuilder answer = new StringBuilder();
        answer.append("根据知识库，").append(contexts.get(0).content());
        if (contexts.size() > 1) {
            answer.append(" 另外可参考：");
            for (int i = 1; i < contexts.size(); i++) {
                answer.append(contexts.get(i).content());
            }
        }

        return new RagAnswer(answer.toString(), contexts);
    }



    @Tool(name = "resume_list_by_user_id", description = "根据用户ID查询该用户的简历列表")
    public List<ResumeEntity> listResumesByUserId(
            @ToolParam(description = "用户ID") Long userId) {

        if (userId == null || userId <= 0) {
            return List.of();
        }

        return resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .orderByDesc(ResumeEntity::getUpdateTime)
                .orderByDesc(ResumeEntity::getId));
    }

    private static int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return 3;
        }
        return Math.min(topK, 10);
    }






    private static SearchResult toSearchResult(KnowledgeDocument document, List<String> queryTerms) {
        String haystack = (document.title() + " " + document.content()).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String term : queryTerms) {
            if (haystack.contains(term)) {
                score += term.length();
            }
        }
        return new SearchResult(document.fileId(), document.title(), document.content(), score);
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "");

        Set<String> dictionary = Set.of(
                "缓考", "申请", "材料", "证明", "考试", "证件", "学生证", "校园卡",
                "补考", "选课", "退课", "课程", "容量", "审批", "生病"
        );

        List<String> terms = new ArrayList<>();
        for (String word : dictionary) {
            if (normalized.contains(word)) {
                terms.add(word);
            }
        }
        if (terms.isEmpty()) {
            terms.add(normalized);
        }
        return terms;
    }

    private record KnowledgeDocument(String fileId, String title, String content) {
    }

    public record SearchResult(String fileId, String title, String content, double score) {
    }

    public record RagAnswer(String answer, List<SearchResult> citations) {
    }
}
