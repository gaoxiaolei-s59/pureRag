package org.puregxl.site.bootstrap.knowledge.service.resource.impl;

import org.junit.jupiter.api.Test;
import org.puregxl.site.bootstrap.config.RustfsProperties;
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
