package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class WakeProofServiceTest {
    private final WakeProofPersistenceService persistence = mock(WakeProofPersistenceService.class);
    private final WakeProofStorage storage = mock(WakeProofStorage.class);
    private final WakeProofService service = new WakeProofService(persistence, storage);

    @Test
    void acceptsMatchingJpegPngAndWebpSignatures() {
        service.createWakeProof(1L, 1L, file("image/jpeg", bytes(32, 0xFF, 0xD8, 0xFF)));
        service.createWakeProof(1L, 2L, file("image/png", bytes(32, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        service.createWakeProof(1L, 3L, file("image/webp", bytes(32, 0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)));
        verify(storage, org.mockito.Mockito.times(3)).upload(anyString(), any());
    }

    @Test
    void acceptsExactTenMegabytesAndRejectsAnythingLarger() {
        service.createWakeProof(1L, 1L, file("image/jpeg", bytes(10 * 1024 * 1024, 0xFF, 0xD8, 0xFF)));
        assertThatThrownBy(() -> service.createWakeProof(
                1L, 2L, file("image/jpeg", bytes(10 * 1024 * 1024 + 1, 0xFF, 0xD8, 0xFF))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMismatchedOrInvalidMagicBytes() {
        assertThatThrownBy(() -> service.createWakeProof(
                1L, 1L, file("image/png", "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createWakeProof(
                1L, 1L, file("image/jpeg", bytes(8, 0x89, 0x50, 0x4E, 0x47))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void compensatesDatabaseFailureAndPreservesOriginalErrorWhenDeleteAlsoFails() {
        doThrow(new BusinessException(com.nunnun.global.exception.ErrorCode.WAKE_PROOF_ALREADY_EXISTS))
                .when(persistence).persistVerifiedProof(any(), any(), anyString());
        doThrow(new WakeProofStorageException("delete failed")).when(storage).delete(anyString());
        assertThatThrownBy(() -> service.createWakeProof(
                1L, 1L, file("image/jpeg", bytes(8, 0xFF, 0xD8, 0xFF))))
                .isInstanceOf(BusinessException.class);
        verify(storage).delete(anyString());
    }

    private MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("image", "ignored-name", contentType, bytes);
    }

    private byte[] bytes(int size, int... prefix) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 1);
        for (int index = 0; index < prefix.length; index++) bytes[index] = (byte) prefix[index];
        return bytes;
    }
}
