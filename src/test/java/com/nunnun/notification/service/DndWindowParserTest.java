package com.nunnun.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DndWindowParserTest {

    private final DndWindowParser parser = new DndWindowParser();

    @Test
    void parsesStrictKoreanDayAndTimeRange() {
        assertThat(parser.parse("월요일, 08:00~11:00"))
                .isEqualTo(new ParsedDndWindow(
                        DayOfWeek.MONDAY,
                        LocalTime.of(8, 0),
                        LocalTime.of(11, 0)
                ));

        assertThat(parser.parse("일요일, 00:00~23:59"))
                .isEqualTo(new ParsedDndWindow(
                        DayOfWeek.SUNDAY,
                        LocalTime.MIDNIGHT,
                        LocalTime.of(23, 59)
                ));
    }

    @ParameterizedTest
    @MethodSource("invalidFormats")
    void rejectsInvalidFormatWithoutCorrection(String text) {
        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_DND_FORMAT)
                );
    }

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void rejectsSameDayOrOvernightRange(String text) {
        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TIME_RANGE)
                );
    }

    private static Stream<Arguments> invalidFormats() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("월요일 08:00~11:00"),
                Arguments.of("월요일,08:00~11:00"),
                Arguments.of("월요일, 8:00~11:00"),
                Arguments.of("월요일, 08:00 - 11:00"),
                Arguments.of("월요일, 08:00~ 11:00"),
                Arguments.of("월요일, 08:00~11:00 "),
                Arguments.of(" 월요일, 08:00~11:00"),
                Arguments.of("월, 08:00~11:00"),
                Arguments.of("MONDAY, 08:00~11:00"),
                Arguments.of("월요일, 24:00~25:00"),
                Arguments.of("월요일, 08:60~11:00"),
                Arguments.of("월요일, 08:00~11:60")
        );
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of("월요일, 11:00~08:00"),
                Arguments.of("월요일, 08:00~08:00"),
                Arguments.of("월요일, 23:00~01:00")
        );
    }
}
