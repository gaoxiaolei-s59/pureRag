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

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * RustFS/S3 兼容对象存储资源服务，封装 bucket 创建、文件上传和回滚逻辑。
 */
@Service
@RequiredArgsConstructor
public class RustfsKnowledgeStorageResourceService implements KnowledgeStorageResourceService {

    private static final String STORAGE_KEEP_OBJECT_KEY = ".keep";
    private static final String CONSOLE_BROWSER_PATH = "/browser/";

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
            return buildConsoleBrowserUrl(bucketName, objectKey);
        } catch (Exception ex) {
            throw new ServiceException("上传 RustFS 文档文件失败：" + ex.getMessage());
        }
    }

    @Override
    public byte[] downloadDocument(String fileUrl) {
        try {
            RustfsObjectLocation objectLocation = parseConsoleBrowserUrl(fileUrl);
            ResponseBytes<GetObjectResponse> responseBytes = rustfsS3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(objectLocation.bucketName())
                    .key(objectLocation.objectKey())
                    .build());
            return responseBytes.asByteArray();
        } catch (Exception ex) {
            throw new ServiceException("读取 RustFS 文档文件失败：" + ex.getMessage());
        }
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

    private String buildConsoleBrowserUrl(String bucketName, String objectKey) {
        String consoleUrl = rustfsProperties.getConsoleUrl();
        if (consoleUrl.endsWith("/")) {
            consoleUrl = consoleUrl.substring(0, consoleUrl.length() - 1);
        }
        return consoleUrl + CONSOLE_BROWSER_PATH + bucketName + "/" + encodeObjectKey(objectKey);
    }

    private RustfsObjectLocation parseConsoleBrowserUrl(String fileUrl) {
        URI uri = URI.create(fileUrl);
        String path = uri.getRawPath();
        int browserPathIndex = path.indexOf(CONSOLE_BROWSER_PATH);
        if (browserPathIndex < 0) {
            throw new ServiceException("RustFS 控制台地址缺少 browser 路径：" + fileUrl);
        }
        String browserPath = path.substring(browserPathIndex + CONSOLE_BROWSER_PATH.length());
        int bucketEndIndex = browserPath.indexOf('/');
        if (bucketEndIndex <= 0 || bucketEndIndex == browserPath.length() - 1) {
            throw new ServiceException("RustFS 控制台地址缺少 bucket 或对象 Key：" + fileUrl);
        }
        String bucketName = URLDecoder.decode(browserPath.substring(0, bucketEndIndex), StandardCharsets.UTF_8);
        String objectKey = URLDecoder.decode(browserPath.substring(bucketEndIndex + 1), StandardCharsets.UTF_8);
        return new RustfsObjectLocation(bucketName, objectKey);
    }

    private String encodeObjectKey(String objectKey) {
        return URLEncoder.encode(objectKey, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record RustfsObjectLocation(String bucketName, String objectKey) {
    }
}
