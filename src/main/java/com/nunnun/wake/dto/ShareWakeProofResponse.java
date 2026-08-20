package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShareWakeProofResponse(
        @JsonProperty("group_ids") List<Long> groupIds
) {
}
