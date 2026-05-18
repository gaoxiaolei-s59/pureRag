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
    void uploadDocumentReturnsRustfsConsoleBrowserUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes());

        String fileUrl = service.uploadDocument("kb-demo", "docs/1/demo.txt", file);

        assertThat(fileUrl).isEqualTo("http://localhost:9001/rustfs/console/browser/kb-demo/docs%2F1%2Fdemo.txt");
    }

    @Test
    void downloadDocumentReadsObjectFromRustfsConsoleBrowserUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(argThat((GetObjectRequest request) ->
                "a-bucket".equals(request.bucket())
                        && "files/bm25_stats/465585969488670805/".equals(request.key()))))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));
        RustfsKnowledgeStorageResourceService service = new RustfsKnowledgeStorageResourceService(new RustfsProperties(), s3Client);

        byte[] content = service.downloadDocument("http://localhost:9001/rustfs/console/browser/a-bucket/files%2Fbm25_stats%2F465585969488670805%2F");

        assertThat(content).isEqualTo("hello".getBytes());
    }
}
