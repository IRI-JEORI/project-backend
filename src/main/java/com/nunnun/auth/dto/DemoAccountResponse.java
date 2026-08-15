package com.nunnun.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.user.entity.User;

public record DemoAccountResponse(
        Long id,
        String nickname,
        @JsonProperty("avatar_url") String avatarUrl
) {
    public static DemoAccountResponse from(User user) {
        return new DemoAccountResponse(user.getId(), user.getNickname(), user.getAvatarUrl());
    }
}
