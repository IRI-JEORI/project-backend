package com.nunnun.roommate.dto;

import com.nunnun.roommate.entity.RoommateBehaviorManual;
import java.time.LocalDateTime;
import java.util.Optional;

public record RoommateBehaviorManualResponse(ManualResponse manual) {

    public static RoommateBehaviorManualResponse from(Optional<RoommateBehaviorManual> manual) {
        return new RoommateBehaviorManualResponse(manual.map(ManualResponse::from).orElse(null));
    }

    public record ManualResponse(
            String content,
            LocalDateTime generatedAt,
            LocalDateTime updatedAt
    ) {
        private static ManualResponse from(RoommateBehaviorManual manual) {
            return new ManualResponse(
                    manual.getContent(),
                    manual.getGeneratedAt(),
                    manual.getUpdatedAt()
            );
        }
    }
}
