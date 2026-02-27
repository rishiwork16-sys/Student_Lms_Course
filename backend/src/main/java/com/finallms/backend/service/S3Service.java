package com.finallms.backend.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Date;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private AmazonS3 s3Client;
    private boolean s3Available = false;
    private Path uploadDir;

    private boolean isBlankOrPlaceholder(String value) {
        if (value == null)
            return true;
        String v = value.trim();
        if (v.isEmpty())
            return true;
        String lower = v.toLowerCase();
        return lower.contains("placeholder") || lower.contains("your_aws") || lower.contains("your_access")
                || lower.equals("changeme");
    }

    private String normalizeBucketRegion(String bucketLocation) {
        if (bucketLocation == null)
            return null;
        String loc = bucketLocation.trim();
        if (loc.isEmpty())
            return null;
        if ("US".equalsIgnoreCase(loc))
            return Regions.US_EAST_1.getName();
        return loc;
    }

    @PostConstruct
    public void init() {
        // Always set up local upload directory (absolute path under working dir)
        try {
            String workDir = System.getProperty("user.dir", ".");
            uploadDir = Paths.get(workDir, "uploads").toAbsolutePath();
            Files.createDirectories(uploadDir);
            System.out.println("[S3Service] Local upload dir: " + uploadDir);
        } catch (IOException e) {
            uploadDir = Paths.get("uploads");
            System.out.println("[S3Service] Fallback upload dir: uploads");
        }

        // Try to initialize S3
        if (isBlankOrPlaceholder(accessKey) || isBlankOrPlaceholder(secretKey) || isBlankOrPlaceholder(bucketName)) {
            System.out.println("[S3Service] No AWS credentials — using Local mode only.");
            return;
        }

        try {
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            String configuredRegion = (region == null || region.isBlank()) ? Regions.US_EAST_1.getName()
                    : region.trim();
            s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(configuredRegion)
                    .build();

            // Validate the connection + possibly correct region
            String bucketRegion = normalizeBucketRegion(s3Client.getBucketLocation(bucketName));
            if (bucketRegion != null && !bucketRegion.equalsIgnoreCase(configuredRegion)) {
                s3Client = AmazonS3ClientBuilder.standard()
                        .withCredentials(new AWSStaticCredentialsProvider(credentials))
                        .withRegion(bucketRegion)
                        .build();
            }
            s3Available = true;
            System.out.println("[S3Service] AWS S3 initialized. Bucket: " + bucketName);
        } catch (Exception e) {
            s3Available = false;
            System.out.println("[S3Service] S3 init failed: " + e.getMessage() + " — falling back to local mode.");
        }
    }

    public String uploadFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("File is empty or null");
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        // ALWAYS save locally first (as temporary storage)
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        Path localFilePath = uploadDir.resolve(fileName);

        try (java.io.InputStream is = file.getInputStream()) {
            Files.copy(is, localFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("[S3Service] Saved locally: " + localFilePath);

        // Also attempt S3 upload if available
        if (s3Available) {
            try {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(file.getSize());
                metadata.setContentType(file.getContentType());

                // Re-open stream for S3
                try (java.io.InputStream s3Is = file.getInputStream()) {
                    s3Client.putObject(new PutObjectRequest(bucketName, fileName, s3Is, metadata));
                    System.out.println("[S3Service] Also uploaded to S3: " + fileName);

                    // SUCCESS! Delete local file to save space
                    try {
                        Files.deleteIfExists(localFilePath);
                        System.out.println("[S3Service] Deleted local copy after S3 upload to save disk space.");
                    } catch (IOException e) {
                        System.out.println("[S3Service] Failed to delete local copy: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.println(
                        "[S3Service] S3 upload failed: " + e.getMessage() + " — will keep local copy for serving.");
            }
        }

        return fileName;
    }

    public String generatePresignedUrl(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80";
        }

        // 1. S3 Priority: If s3 is available, generate a presigned URL
        if (s3Available && s3Client != null) {
            try {
                Date expiration = new Date();
                expiration.setTime(expiration.getTime() + 1000 * 60 * 60 * 2); // 2 hours
                GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, fileName)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(expiration);
                return s3Client.generatePresignedUrl(req).toString();
            } catch (Exception e) {
                System.out.println("[S3Service] Presign failed: " + e.getMessage());
            }
        }

        // 2. LOCAL FILE Fallback: check if it exists (use full URL so remote frontend
        // can access it)
        if (uploadDir != null) {
            Path filePath = uploadDir.resolve(fileName);
            if (Files.exists(filePath)) {
                return "https://lmsapi.skilledup.tech/uploads/" + fileName;
            }
        }

        // 3. Fallback placeholders
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm")) {
            return "https://www.w3schools.com/html/mov_bbb.mp4";
        }
        return "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800&q=80";
    }

    public void deleteFile(String fileName) {
        // Delete locally
        if (uploadDir != null) {
            try {
                Files.deleteIfExists(uploadDir.resolve(fileName));
            } catch (IOException ignored) {
            }
        }
        // Delete from S3
        if (s3Available && s3Client != null) {
            try {
                s3Client.deleteObject(bucketName, fileName);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean fileExists(String fileName) {
        // Check locally first
        if (uploadDir != null && Files.exists(uploadDir.resolve(fileName))) {
            return true;
        }
        // Check S3
        if (s3Available && s3Client != null) {
            try {
                return s3Client.doesObjectExist(bucketName, fileName);
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
