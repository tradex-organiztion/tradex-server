package hello.tradexserver.service;

import hello.tradexserver.config.AwsS3Properties;
import hello.tradexserver.exception.BusinessException;
import hello.tradexserver.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(60);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Properties props;

    /**
     * 파일을 S3에 업로드하고 접근 URL을 반환합니다.
     *
     * @param userId 업로드하는 사용자 ID
     * @param file   업로드할 이미지 파일
     * @param folder S3 내 폴더 경로 (예: "screenshots")
     * @return 업로드된 파일의 S3 URL
     */
    public String upload(Long userId, MultipartFile file, String folder) {
        validateImageFile(file);

        String key = buildKey(folder, userId, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            log.info("[S3Service] 파일 업로드 완료 - userId: {}, key: {}", userId, key);

            return buildUrl(key);

        } catch (IOException e) {
            log.error("[S3Service] 파일 업로드 실패 - userId: {}, key: {}", userId, key, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * S3 파일에 대한 Presigned URL을 생성합니다. (유효 시간: 60분)
     */
    public String generatePresignedUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(props.getBucketName())
                        .key(key)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * S3에서 파일을 삭제합니다.
     *
     * @param fileUrl 삭제할 파일의 S3 URL
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        String key = extractKeyFromUrl(fileUrl);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("[S3Service] 파일 삭제 완료 - key: {}", key);

        } catch (Exception e) {
            log.error("[S3Service] 파일 삭제 실패 - key: {}", key, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private String buildKey(String folder, Long userId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return folder + "/" + userId + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
    }

    private String buildUrl(String key) {
        return "https://" + props.getBucketName() + ".s3." + props.getRegion() + ".amazonaws.com/" + key;
    }

    private String extractKeyFromUrl(String url) {
        String prefix = "https://" + props.getBucketName() + ".s3." + props.getRegion() + ".amazonaws.com/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        // fallback: URL 마지막 경로 이후
        return url.substring(url.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}