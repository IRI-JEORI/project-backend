package com.nunnun.roommate.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoommateComplaintRequest(@NotBlank String content) {
}
