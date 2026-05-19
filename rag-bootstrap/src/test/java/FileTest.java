import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.puregxl.site.bootstrap.RagTestApplication;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@SpringBootTest(classes = RagTestApplication.class)
public class FileTest {

    @Autowired
    private KnowledgeStorageResourceService storageResourceService;

    @Test
    void downloadFromRustfsAndParseByTika() throws Exception {
        String fileUrl = System.getProperty("rustfs.file-url",
                "rustfs://test/docs/2056311497486430209/dummy.pdf");

        MultipartFile file = storageResourceService.downloadDocumentAsMultipartFile(fileUrl);

        Tika tika = new Tika();
        String text;
        try (InputStream inputStream = file.getInputStream()) {
            text = tika.parseToString(inputStream);
        }

        System.out.println("文件名：" + file.getOriginalFilename());
        System.out.println("文件类型：" + file.getContentType());
        System.out.println("文件大小：" + file.getSize());
        System.out.println("解析内容：");
        System.out.println(text);
    }
}
