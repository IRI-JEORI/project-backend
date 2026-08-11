package com.nunnun.wake.storage;

public class UnavailableWakeProofStorage implements WakeProofStorage {

    @Override
    public void upload(String objectKey, org.springframework.web.multipart.MultipartFile image) {
        throw new WakeProofStorageException("Wake proof storage is not configured.");
    }

    @Override
    public void delete(String objectKey) {
        throw new WakeProofStorageException("Wake proof storage is not configured.");
    }
}
