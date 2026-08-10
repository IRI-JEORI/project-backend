package com.nunnun.auth.dto;

import com.nunnun.user.entity.User;

public record SignUpResponse(Long userId, String nickname, String email) {

    public static SignUpResponse from(User user) {
        return new SignUpResponse(user.getId(), user.getNickname(), user.getEmail());
    }
}
