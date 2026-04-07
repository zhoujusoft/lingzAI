package lingzhou.agent.backend.business.datasets.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO 文件存储服务
 */
@Component
@Slf4j
public class MinioService {

    @Value("${minio.endpoint:http://minio:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:documents}")
    private String bucket;

    private MinioClient minioClient;

    @PostConstruct
    public void init() throws Exception {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 确保 bucket 存在
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("创建 MinIO bucket: {}", bucket);
        }
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param kbId 知识库 ID
     * @param docId 文档 ID
     * @return 对象名称（路径）
     */
    public String uploadFile(MultipartFile file, Long kbId, Long docId) throws Exception {
        String objectName =
                String.format("documents/%d/%d/%s%s", kbId, docId, java.util.UUID.randomUUID(), extractExtension(file));
        putMultipartFile(objectName, file);
        log.info("文件上传成功：bucket={}, objectName={}, size={}", bucket, objectName, file.getSize());
        return objectName;
    }

    public String uploadChatFile(MultipartFile file, Long userId, String fileId) throws Exception {
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        String objectName = String.format("chat-files/%d/%s%s", safeUserId, fileId, extractExtension(file));
        putMultipartFile(objectName, file);
        log.info("聊天附件上传成功：bucket={}, objectName={}, size={}", bucket, objectName, file.getSize());
        return objectName;
    }

    /**
     * 获取文件输入流
     *
     * @param objectName 对象名称
     * @return 输入流
     */
    public InputStream getFile(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        log.info("文件删除成功：bucket={}, objectName={}", bucket, objectName);
    }

    private void putMultipartFile(String objectName, MultipartFile file) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
    }

    private String extractExtension(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}
