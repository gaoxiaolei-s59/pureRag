package org.puregxl.site.knowledge.service.resource.impl;

import lombok.RequiredArgsConstructor;
import org.puregxl.site.config.RustfsProperties;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

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
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            rustfsS3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .contentType(contentType)
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
        return RUSTFS_URL_SCHEME + "://" + bucketName + "/" + encodeObjectKey(objectKey);
    }

    private RustfsObjectLocation parseRustfsUrl(String fileUrl) {
        // RustFS 内部地址允许对象 Key 使用中文、空格等文件名字符。新地址会编码路径；历史地址可能未编码，
        // 因此先尝试标准 URI 解析，失败后退回到 scheme 前缀切分，保证旧数据仍可被读取。
        RustfsObjectLocation objectLocation = parseByUri(fileUrl);
        if (objectLocation != null) {
            return objectLocation;
        }
        String prefix = RUSTFS_URL_SCHEME + "://";
        if (fileUrl == null || !fileUrl.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new ServiceException("RustFS 文件地址格式不正确：" + fileUrl);
        }
        String location = fileUrl.substring(prefix.length());
        int slashIndex = location.indexOf('/');
        if (slashIndex <= 0 || slashIndex == location.length() - 1) {
            throw new ServiceException("RustFS 文件地址缺少对象 Key：" + fileUrl);
        }
        return new RustfsObjectLocation(location.substring(0, slashIndex), decodeObjectKey(location.substring(slashIndex + 1)));
    }

    private RustfsObjectLocation parseByUri(String fileUrl) {
        try {
            URI uri = URI.create(fileUrl);
            if (!RUSTFS_URL_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            String objectKey = uri.getRawPath() == null || uri.getRawPath().length() <= 1
                    ? null
                    : uri.getRawPath().substring(1);
            if (objectKey == null || objectKey.isBlank()) {
                throw new ServiceException("RustFS 文件地址缺少对象 Key：" + fileUrl);
            }
            return new RustfsObjectLocation(uri.getHost(), decodeObjectKey(objectKey));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String encodeObjectKey(String objectKey) {
        return Arrays.stream(objectKey.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private String decodeObjectKey(String objectKey) {
        return Arrays.stream(objectKey.split("/", -1))
                .map(segment -> URLDecoder.decode(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
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
