package com.nunnun.notification.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DndWindowParser {

    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "^(월요일|화요일|수요일|목요일|금요일|토요일|일요일), "
                    + "((?:[01][0-9]|2[0-3]):[0-5][0-9])~"
                    + "((?:[01][0-9]|2[0-3]):[0-5][0-9])$"
    );
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, DayOfWeek> DAY_BY_KOREAN_NAME = Map.of(
            "월요일", DayOfWeek.MONDAY,
            "화요일", DayOfWeek.TUESDAY,
            "수요일", DayOfWeek.WEDNESDAY,
            "목요일", DayOfWeek.THURSDAY,
            "금요일", DayOfWeek.FRIDAY,
            "토요일", DayOfWeek.SATURDAY,
            "일요일", DayOfWeek.SUNDAY
    );

    public ParsedDndWindow parse(String text) {
        if (text == null) {
            throw new BusinessException(ErrorCode.INVALID_DND_FORMAT);
        }

        Matcher matcher = INPUT_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_DND_FORMAT);
        }

        LocalTime startTime = LocalTime.parse(matcher.group(2), TIME_FORMATTER);
        LocalTime endTime = LocalTime.parse(matcher.group(3), TIME_FORMATTER);
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }

        return new ParsedDndWindow(
                DAY_BY_KOREAN_NAME.get(matcher.group(1)),
                startTime,
                endTime
        );
    }
}
