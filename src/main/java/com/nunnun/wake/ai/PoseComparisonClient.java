package com.nunnun.wake.ai;

public interface PoseComparisonClient {

    int compare(String referenceImageUrl, String submittedImageUrl, String poseDescription);
}
