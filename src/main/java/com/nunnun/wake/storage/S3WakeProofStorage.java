package com.nunnun.wake.storage;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class S3WakeProofStorage implements WakeProofStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3WakeProofStorage(S3Client s3Client, S3Presigner s3Presigner, String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    @Override
    public String createReadUrl(String objectKey, Duration validFor) {
        try {
            GetObjectRequest getObject = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(validFor).getObjectRequest(getObject).build())
                    .url().toString();
        } catch (RuntimeException exception) {
            throw new WakeProofStorageException("Failed to create wake proof read URL.", exception);
        }
    }

    @Override
    public void upload(String objectKey, MultipartFile image) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(image.getContentType())
                            .build(),
                    RequestBody.fromBytes(image.getBytes())
            );
        } catch (IOException | RuntimeException exception) {
            throw new WakeProofStorageException("Failed to upload wake proof image.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException exception) {
            throw new WakeProofStorageException("Failed to delete wake proof image.", exception);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try {
            List<StoredObject> objects = new ArrayList<>();
            String continuationToken = null;
            do {
                ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).continuationToken(continuationToken).build());
                response.contents().forEach(object -> objects.add(new StoredObject(object.key(), object.lastModified())));
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);
            return List.copyOf(objects);
        } catch (RuntimeException exception) {
            throw new WakeProofStorageException("Failed to list wake proof images.", exception);
        }
    }
}
