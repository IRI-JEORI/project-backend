package com.nunnun.wake.storage;

import org.springframework.web.multipart.MultipartFile;

public interface WakeProofStorage {

    void upload(String objectKey, MultipartFile image);

    void delete(String objectKey);
}
