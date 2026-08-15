package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WakeTargetsResponse(
        @JsonProperty("wake_targets") List<WakeTargetResponse> wakeTargets
) {
}
