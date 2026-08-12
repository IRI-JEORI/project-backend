package com.nunnun.schedule.ai;

import java.util.List;

public interface ScheduleAnalyzer {

    List<AnalyzedFixedSchedule> analyze(byte[] imageBytes, String contentType);
}
