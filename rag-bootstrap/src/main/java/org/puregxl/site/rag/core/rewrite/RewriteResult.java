package org.puregxl.site.rag.core.rewrite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewriteResult {

    /**
     * 改写后的用户问题
     */
    private String rewrittenQuestion;

    /**
     * 字问题
     */
    private List<String> subQuestions;
}
