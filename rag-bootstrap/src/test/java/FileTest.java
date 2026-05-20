import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.puregxl.site.bootstrap.RagTestApplication;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@SpringBootTest(classes = RagTestApplication.class)
public class FileTest {

    @Autowired
    private KnowledgeStorageResourceService storageResourceService;

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void test() {
        List<Float> test = embeddingService.embed("你好");
    }
}
