package org.puregxl.site.knowledge.service.resource.impl;

import org.junit.jupiter.api.Test;
import org.puregxl.site.config.RustfsProperties;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RustfsKnowledgeStorageResourceServiceTest {

    @Test
    void uploadDocumentReturnsRustfsUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes());

        String fileUrl = service.uploadDocument("kb-demo", "docs/1/demo.txt", file);

        assertThat(fileUrl).isEqualTo("rustfs://kb-demo/docs/1/demo.txt");
    }

    @Test
    void uploadDocumentEncodesUnsafeObjectKeyCharsInRustfsUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);
        MockMultipartFile file = new MockMultipartFile("file", "RAG 测试文档：电商平台业务说明.md", "text/markdown", "hello".getBytes());

        String fileUrl = service.uploadDocument("test", "docs/2057025620384362497/RAG 测试文档：电商平台业务说明.md", file);

        assertThat(fileUrl).isEqualTo("rustfs://test/docs/2057025620384362497/RAG%20%E6%B5%8B%E8%AF%95%E6%96%87%E6%A1%A3%EF%BC%9A%E7%94%B5%E5%95%86%E5%B9%B3%E5%8F%B0%E4%B8%9A%E5%8A%A1%E8%AF%B4%E6%98%8E.md");
    }

    @Test
    void downloadDocumentReadsObjectFromRustfsUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(argThat((GetObjectRequest request) ->
                "a-bucket".equals(request.bucket())
                        && "files/bm25_stats/465585969488670805/".equals(request.key()))))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);

        byte[] content = service.downloadDocument("rustfs://a-bucket/files/bm25_stats/465585969488670805/");

        assertThat(content).isEqualTo("hello".getBytes());
    }

    @Test
    void downloadDocumentSupportsEncodedAndLegacyUnencodedRustfsUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(argThat((GetObjectRequest request) ->
                "test".equals(request.bucket())
                        && "docs/2057025620384362497/RAG 测试文档：电商平台业务说明.md".equals(request.key()))))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);

        byte[] encodedContent = service.downloadDocument("rustfs://test/docs/2057025620384362497/RAG%20%E6%B5%8B%E8%AF%95%E6%96%87%E6%A1%A3%EF%BC%9A%E7%94%B5%E5%95%86%E5%B9%B3%E5%8F%B0%E4%B8%9A%E5%8A%A1%E8%AF%B4%E6%98%8E.md");
        byte[] legacyContent = service.downloadDocument("rustfs://test/docs/2057025620384362497/RAG 测试文档：电商平台业务说明.md");

        assertThat(encodedContent).isEqualTo("hello".getBytes());
        assertThat(legacyContent).isEqualTo("hello".getBytes());
    }

    @Test
    void downloadDocumentAsMultipartFileWrapsDownloadedObject() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(argThat((GetObjectRequest request) ->
                "a-bucket".equals(request.bucket())
                        && "docs/1/demo.txt".equals(request.key()))))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);

        var multipartFile = service.downloadDocumentAsMultipartFile("rustfs://a-bucket/docs/1/demo.txt");

        assertThat(multipartFile.getName()).isEqualTo("file");
        assertThat(multipartFile.getOriginalFilename()).isEqualTo("demo.txt");
        assertThat(multipartFile.getContentType()).isEqualTo("text/plain");
        assertThat(multipartFile.getBytes()).isEqualTo("hello".getBytes());
    }
}
