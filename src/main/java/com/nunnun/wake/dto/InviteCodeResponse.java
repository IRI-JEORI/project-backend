package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InviteCodeResponse(@JsonProperty("invite_code") String inviteCode) {
}
