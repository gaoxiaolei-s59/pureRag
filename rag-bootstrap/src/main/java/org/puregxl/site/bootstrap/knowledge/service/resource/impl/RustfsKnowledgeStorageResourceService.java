package org.puregxl.site.bootstrap.knowledge.service.resource.impl;

import lombok.RequiredArgsConstructor;
import org.puregxl.site.bootstrap.config.RustfsProperties;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * RustFS/S3 兼容对象存储资源服务，封装 bucket 命名、创建和回滚逻辑。
 */
@Service
@RequiredArgsConstructor
public class RustfsKnowledgeStorageResourceService implements KnowledgeStorageResourceService {

    private static final Pattern STORAGE_BUCKET_INVALID_CHAR_PATTERN = Pattern.compile("[^a-z0-9.-]");
    private static final Pattern STORAGE_BUCKET_SEPARATOR_PATTERN = Pattern.compile("[.-]{2,}");
    private static final String STORAGE_KEEP_OBJECT_KEY = ".keep";
    private static final String STORAGE_BUCKET_PREFIX = "kb-";
    private static final int STORAGE_BUCKET_MAX_LENGTH = 63;

    private final RustfsProperties rustfsProperties;
    private final S3Client rustfsS3Client;

    @Override
    public String buildBucketName(String resourceName) {
        CRC32 crc32 = new CRC32();
        crc32.update(resourceName.getBytes(StandardCharsets.UTF_8));
        String suffix = Long.toHexString(crc32.getValue());
        String normalized = STORAGE_BUCKET_INVALID_CHAR_PATTERN
                .matcher(resourceName.trim().toLowerCase(Locale.ROOT))
                .replaceAll("-");
        normalized = STORAGE_BUCKET_SEPARATOR_PATTERN.matcher(normalized).replaceAll("-");
        normalized = trimBucketEdge(normalized);
        if (normalized.isBlank()) {
            normalized = "bucket";
        }

        int maxNameLength = STORAGE_BUCKET_MAX_LENGTH - STORAGE_BUCKET_PREFIX.length() - suffix.length() - 1;
        if (normalized.length() > maxNameLength) {
            normalized = trimBucketEdge(normalized.substring(0, maxNameLength));
        }
        return STORAGE_BUCKET_PREFIX + normalized + "-" + suffix;
    }

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

    private String trimBucketEdge(String bucketName) {
        int start = 0;
        int end = bucketName.length();
        while (start < end && !Character.isLetterOrDigit(bucketName.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(bucketName.charAt(end - 1))) {
            end--;
        }
        return bucketName.substring(start, end);
    }
}
