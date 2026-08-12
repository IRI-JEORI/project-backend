package com.nunnun.user.dto;

import com.nunnun.user.entity.User;

public record UserResponse(Long id, String nickname, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getEmail());
    }
}
