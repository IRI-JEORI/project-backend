package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WakeProofService {

    private static final Logger log = LoggerFactory.getLogger(WakeProofService.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

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
            wakeProofStorage.delete(objectKey);
        } catch (WakeProofStorageException ignored) {
            log.error("Wake proof compensation deletion failed; orphan sweep will retry.");
        }
    }
}
