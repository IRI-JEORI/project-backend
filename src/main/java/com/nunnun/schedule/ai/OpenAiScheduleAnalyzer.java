package com.nunnun.schedule.ai;

import com.nunnun.global.config.OpenAiProperties;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.StructuredResponseCreateParams;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiScheduleAnalyzer implements ScheduleAnalyzer {

    private static final String ANALYSIS_INSTRUCTIONS = """
            Analyze the provided timetable image. Extract only schedules that are clearly visible.
            Return each course name as title. Do not infer missing information.
            dayOfWeek must be one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
            startTime and endTime must use 24-hour HH:mm format, and startTime must be earlier than endTime.
            Return no explanatory text; return only the structured result.
            """;

    private final OpenAiProperties openAiProperties;

    public OpenAiScheduleAnalyzer(OpenAiProperties openAiProperties) {
        this.openAiProperties = openAiProperties;
    }

    @Override
    public List<AnalyzedFixedSchedule> analyze(byte[] imageBytes, String contentType) {
        if (!StringUtils.hasText(openAiProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
        }

        try {
            String imageDataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
            StructuredResponseCreateParams<ScheduleAnalysisOutput> request = ResponseCreateParams.builder()
                    .model(openAiProperties.getModel())
                    .instructions(ANALYSIS_INSTRUCTIONS)
                    .inputOfResponse(List.of(ResponseInputItem.ofMessage(ResponseInputItem.Message.builder()
                            .role(ResponseInputItem.Message.Role.USER)
                            .addContent(ResponseInputImage.builder().imageUrl(imageDataUrl).build())
                            .build())))
                    .text(ScheduleAnalysisOutput.class)
                    .build();

            ScheduleAnalysisOutput output = createClient().responses().create(request).output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED));
            if (output.schedules == null || output.schedules.isEmpty()) {
                throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
            }
            return output.schedules.stream()
                    .map(schedule -> new AnalyzedFixedSchedule(
                            schedule.title,
                            schedule.dayOfWeek,
                            schedule.startTime,
                            schedule.endTime
                    ))
                    .toList();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
        }
    }

    private OpenAIClient createClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(openAiProperties.getApiKey())
                .timeout(Duration.ofSeconds(openAiProperties.getTimeoutSeconds()))
                .maxRetries(0)
                .build();
    }

    public static class ScheduleAnalysisOutput {
        public List<ScheduleOutput> schedules;
    }

    public static class ScheduleOutput {
        public String title;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
    }
}
