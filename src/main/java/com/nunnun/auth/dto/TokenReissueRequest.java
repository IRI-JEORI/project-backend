package com.nunnun.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(@NotBlank String refreshToken) {
}
