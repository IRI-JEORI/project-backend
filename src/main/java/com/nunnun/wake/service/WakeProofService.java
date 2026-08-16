package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.ai.PoseComparisonClient;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WakeProofService {

    private static final Logger log = LoggerFactory.getLogger(WakeProofService.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Duration IMAGE_URL_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final WakeProofPersistenceService persistenceService;
    private final WakeProofStorage storage;
    private final PoseComparisonClient poseComparisonClient;

    public WakeProofService(
            WakeProofPersistenceService persistenceService,
            WakeProofStorage storage,
            PoseComparisonClient poseComparisonClient
    ) {
        this.persistenceService = persistenceService;
        this.storage = storage;
        this.poseComparisonClient = poseComparisonClient;
    }

    public CreateWakeProofResponse createWakeProof(Long userId, Long requestId, MultipartFile image) {
        validateImage(image);
        WakeProofPersistenceService.ProofPreparation preparation = persistenceService.prepare(requestId, userId);
        String referenceUrl;
        try {
            referenceUrl = storage.createReadUrl(preparation.referenceImageObjectKey(), IMAGE_URL_VALIDITY);
        } catch (WakeProofStorageException exception) {
            throw new BusinessException(ErrorCode.POSE_ANALYSIS_FAILED);
        }

        String objectKey = createObjectKey(requestId, image.getContentType());
        try {
            storage.upload(objectKey, image);
        } catch (WakeProofStorageException exception) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_UPLOAD_FAILED);
        }

        try {
            String submittedUrl = storage.createReadUrl(objectKey, IMAGE_URL_VALIDITY);
            int score = poseComparisonClient.compare(referenceUrl, submittedUrl, preparation.poseDescription());
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("Pose score is outside the supported range.");
            }
            CreateWakeProofResponse response = persistenceService.applyResult(requestId, userId, objectKey, score);
            if (response.poseMatchResult() == PoseMatchResult.FAIL) {
                safelyDeleteUploadedObject(objectKey);
            }
            return response;
        } catch (BusinessException exception) {
            safelyDeleteUploadedObject(objectKey);
            throw exception;
        } catch (RuntimeException exception) {
            safelyDeleteUploadedObject(objectKey);
            throw new BusinessException(ErrorCode.POSE_ANALYSIS_FAILED);
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES
                || !ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_PROOF_IMAGE);
        }
        try {
            if (!matchesSignature(image.getContentType(), image.getBytes())) {
                throw new BusinessException(ErrorCode.INVALID_WAKE_PROOF_IMAGE);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_PROOF_IMAGE);
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                    && bytes.length >= 12
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) return false;
        }
        return true;
    }

    private String createObjectKey(Long requestId, String contentType) {
        return "wake-proofs/" + requestId + "/" + UUID.randomUUID() + extensionFor(contentType);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException(ErrorCode.INVALID_WAKE_PROOF_IMAGE);
        };
    }

    private void safelyDeleteUploadedObject(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (WakeProofStorageException ignored) {
            log.error("Wake proof image deletion failed; orphan sweep will retry.");
        }
    }
}
