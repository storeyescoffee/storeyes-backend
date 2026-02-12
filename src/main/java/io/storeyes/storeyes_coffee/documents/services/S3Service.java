package io.storeyes.storeyes_coffee.documents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
public class S3Service {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name:storeyes-documents}")
    private String bucketName;
    
    @Value("${aws.s3.region:eu-south-2}")
    private String region;
    
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    /**
     * Upload a file to S3
     * @param file The file to upload
     * @param storeCode The store code for the folder structure
     * @return The S3 URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String storeCode) {
        try {
            // Generate unique file name to avoid conflicts
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;
            
            // S3 key: store_code/file_name
            String key = storeCode + "/" + fileName;
            
            // Upload to S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            // Generate URL
            String url = generateUrl(storeCode, fileName);
            
            log.info("File uploaded successfully to S3: {}", url);
            return url;
            
        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        } catch (S3Exception e) {
            log.error("S3 error while uploading file", e);
            throw new RuntimeException("S3 error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete a file from S3
     * @param url The S3 URL of the file to delete
     */
    public void deleteFile(String url) {
        try {
            // Extract key from URL
            // URL format: https://storeyes-documents.s3.eu-south-2.amazonaws.com/<store-code>/<file-name>
            String key = extractKeyFromUrl(url);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            
            log.info("File deleted successfully from S3: {}", url);
            
        } catch (S3Exception e) {
            log.error("S3 error while deleting file", e);
            throw new RuntimeException("S3 error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate S3 URL for a file
     */
    private String generateUrl(String storeCode, String fileName) {
        // URL encode the file name to handle special characters
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20"); // Replace + with %20 for better compatibility
        
        return String.format("https://%s.s3.%s.amazonaws.com/%s/%s", 
                bucketName, region, storeCode, encodedFileName);
    }
    
    /**
     * Extract S3 key from URL
     */
    private String extractKeyFromUrl(String url) {
        // URL format: https://storeyes-documents.s3.eu-south-2.amazonaws.com/<store-code>/<file-name>
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        if (url.startsWith(prefix)) {
            String key = url.substring(prefix.length());
            // Decode URL-encoded characters
            try {
                return java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // If decoding fails, return as-is
                return key;
            }
        }
        throw new IllegalArgumentException("Invalid S3 URL format: " + url);
    }
}

