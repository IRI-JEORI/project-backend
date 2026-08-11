package com.nunnun.roommate.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoommateComplaintRequest(@NotBlank String content) {
}
