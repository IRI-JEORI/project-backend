package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WakeProofService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final WakeProofPersistenceService wakeProofPersistenceService;
    private final WakeProofStorage wakeProofStorage;

    public WakeProofService(
            WakeProofPersistenceService wakeProofPersistenceService,
            WakeProofStorage wakeProofStorage
    ) {
        this.wakeProofPersistenceService = wakeProofPersistenceService;
        this.wakeProofStorage = wakeProofStorage;
    }

    public CreateWakeProofResponse createWakeProof(Long userId, Long requestId, MultipartFile image) {
        validateImage(image);
        wakeProofPersistenceService.validateProofCreation(requestId, userId);
        String objectKey = createObjectKey(requestId, image.getContentType());
        try {
            wakeProofStorage.upload(objectKey, image);
        } catch (WakeProofStorageException exception) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_UPLOAD_FAILED);
        }
        try {
            return wakeProofPersistenceService.persistVerifiedProof(requestId, userId, objectKey);
        } catch (RuntimeException exception) {
            safelyDeleteUploadedObject(objectKey);
            throw exception;
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_PROOF_IMAGE);
        }
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
            wakeProofStorage.delete(objectKey);
        } catch (WakeProofStorageException ignored) {
            // The original persistence failure remains the API error; cleanup retries handle no DB row only indirectly.
        }
    }
}
