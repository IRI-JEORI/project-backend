package com.nunnun.wake.dto;

import com.nunnun.user.entity.User;

public record WakeRequestUserResponse(Long id, String nickname) {

    public static WakeRequestUserResponse from(User user) {
        return new WakeRequestUserResponse(user.getId(), user.getNickname());
    }
}
