package com.nunnun.wake.storage;

import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.time.Duration;
import java.util.List;

public interface WakeProofStorage {

    void upload(String objectKey, MultipartFile image);

    void delete(String objectKey);

    String createReadUrl(String objectKey, Duration validFor);

    List<StoredObject> list(String prefix);

    record StoredObject(String key, Instant lastModified) {
    }
}
