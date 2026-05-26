import org.junit.jupiter.api.Test;
import org.puregxl.site.RagTestApplication;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@SpringBootTest(classes = RagTestApplication.class)
public class FileTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void test() {
        List<Float> chineseVector = embeddingService.embed("你好");
        List<Float> englishVector = embeddingService.embed("hello");
        double similarity = cosineSimilarity(chineseVector, englishVector);

        System.out.println("你好 vs hello cosine similarity = " + similarity);
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            throw new IllegalArgumentException("向量不能为空，且两个向量维度必须一致");
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.size(); i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            throw new IllegalArgumentException("向量模长不能为 0");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }



}
