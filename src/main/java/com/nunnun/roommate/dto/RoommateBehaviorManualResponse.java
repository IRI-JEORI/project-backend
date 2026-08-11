package com.nunnun.roommate.dto;

import com.nunnun.roommate.entity.RoommateBehaviorManual;
import java.time.LocalDateTime;

public record RoommateBehaviorManualResponse(
        String content,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt
) {
    public static RoommateBehaviorManualResponse from(RoommateBehaviorManual manual) {
        return new RoommateBehaviorManualResponse(
                manual.getContent(),
                manual.getGeneratedAt(),
                manual.getUpdatedAt()
        );
    }
}
