package org.puregxl.site.bootstrap.knowledge.service.resource.impl;

import lombok.RequiredArgsConstructor;
import org.puregxl.site.bootstrap.config.RustfsProperties;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

/**
 * RustFS/S3 兼容对象存储资源服务，封装 bucket 创建、文件上传和回滚逻辑。
 */
@Service
@RequiredArgsConstructor
public class RustfsKnowledgeStorageResourceService implements KnowledgeStorageResourceService {

    private static final String STORAGE_KEEP_OBJECT_KEY = ".keep";
    private static final String RUSTFS_URL_SCHEME = "rustfs";

    private final RustfsProperties rustfsProperties;
    private final S3Client rustfsS3Client;

    @Override
    public void createStorage(String bucketName) {
        try {
            try {
                rustfsS3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
            } catch (NoSuchBucketException ex) {
                if (!Boolean.TRUE.equals(rustfsProperties.getCreateBucketIfMissing())) {
                    throw new ServiceException("RustFS Bucket 不存在：" + bucketName);
                }
                rustfsS3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404 && Boolean.TRUE.equals(rustfsProperties.getCreateBucketIfMissing())) {
                    rustfsS3Client.createBucket(CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .build());
                } else {
                    throw ex;
                }
            }

            // RustFS 没有真实目录概念，放一个占位对象表示该 bucket 已初始化。
            rustfsS3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(STORAGE_KEEP_OBJECT_KEY)
                            .contentType("application/octet-stream")
                            .build(),
                    RequestBody.empty());
        } catch (Exception ex) {
            throw new ServiceException("创建 RustFS 文件存储失败：" + ex.getMessage());
        }
    }

    @Override
    public String uploadDocument(String bucketName, String objectKey, MultipartFile file) {
        try {
            rustfsS3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return buildRustfsUrl(bucketName, objectKey);
        } catch (Exception ex) {
            throw new ServiceException("上传 RustFS 文档文件失败：" + ex.getMessage());
        }
    }

    @Override
    public byte[] downloadDocument(String fileUrl) {
        try {
            RustfsObjectLocation objectLocation = parseRustfsUrl(fileUrl);
            ResponseBytes<GetObjectResponse> responseBytes = rustfsS3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(objectLocation.bucketName())
                    .key(objectLocation.objectKey())
                    .build());
            return responseBytes.asByteArray();
        } catch (Exception ex) {
            throw new ServiceException("读取 RustFS 文档文件失败：" + ex.getMessage());
        }
    }

    @Override
    public MultipartFile downloadDocumentAsMultipartFile(String fileUrl) {
        RustfsObjectLocation objectLocation = parseRustfsUrl(fileUrl);
        byte[] documentBytes = downloadDocument(fileUrl);
        String filename = resolveFilename(objectLocation.objectKey());
        String contentType = URLConnection.guessContentTypeFromName(filename);
        return new DownloadedMultipartFile("file", filename, contentType, documentBytes);
    }

    /**
     * 删除对应文档的对象
     * @param bucketName bucket 名称
     * @param objectKey 对象 key
     */
    @Override
    public void deleteDocument(String bucketName, String objectKey) {
        try {
            rustfsS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new ServiceException("删除 RustFS 文档文件失败：" + ex.getMessage());
        }
    }

    @Override
    public void rollbackStorage(String bucketName) {
        try {
            rustfsS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(STORAGE_KEEP_OBJECT_KEY)
                    .build());
        } catch (Exception ignored) {
            // 创建流程失败后的兜底清理，清理失败不覆盖原始异常。
        }
    }

    private String buildRustfsUrl(String bucketName, String objectKey) {
        return RUSTFS_URL_SCHEME + "://" + bucketName + "/" + objectKey;
    }

    private RustfsObjectLocation parseRustfsUrl(String fileUrl) {
        URI uri = URI.create(fileUrl);
        if (!RUSTFS_URL_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new ServiceException("RustFS 文件地址格式不正确：" + fileUrl);
        }
        String objectKey = uri.getPath() == null || uri.getPath().length() <= 1 ? null : uri.getPath().substring(1);
        if (objectKey == null || objectKey.isBlank()) {
            throw new ServiceException("RustFS 文件地址缺少对象 Key：" + fileUrl);
        }
        return new RustfsObjectLocation(uri.getHost(), objectKey);
    }

    private String resolveFilename(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == objectKey.length() - 1) {
            return objectKey;
        }
        return objectKey.substring(slashIndex + 1);
    }

    private record DownloadedMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("下载文件包装对象不支持 transferTo");
        }
    }

    private record RustfsObjectLocation(String bucketName, String objectKey) {
    }
}
