package com.nunnun.wake.service;

import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeRequestExpirationService {
    private final WakeRequestRepository requests;
    private final Clock clock;

    public WakeRequestExpirationService(WakeRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wake.request-expiration-fixed-delay-ms:60000}")
    @Transactional
    public void expireUnverifiedRequests() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(10);
        requests.findIdsByStatusAndRequestedAtLessThanEqual(WakeRequestStatus.SENT, cutoff)
                .forEach(this::expireWithLock);
    }

    @Transactional
    public void expireWithLock(Long requestId) {
        WakeRequest request = requests.findByIdForUpdate(requestId).orElse(null);
        if (request != null && request.getStatus() == WakeRequestStatus.SENT
                && !request.getRequestedAt().isAfter(LocalDateTime.now(clock).minusMinutes(10))) {
            request.expire();
        }
    }
}
