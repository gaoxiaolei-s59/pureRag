package org.puregxl.site.rag.core.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionIntent {
    /**
     * 字问题文本
     */
    private String subQuestion;

    /**
     * 字问题候选分数
     */
    private List<NodeScore> nodeScores;
}
