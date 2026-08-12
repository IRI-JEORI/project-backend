package com.nunnun.roommate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoommateComplaintRequest(@NotBlank @Size(max = 300) String content) {
}
