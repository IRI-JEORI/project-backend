package com.nunnun.notification.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageFactory {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public NotificationMessage wakeRequest(String senderNickname) {
        return new NotificationMessage("깨우기 요청이 왔어요", senderNickname + "님이 깨우고 있어요.");
    }

    public NotificationMessage roommateSleeping(String roommateNickname) {
        return new NotificationMessage("룸메이트가 잠들었어요", roommateNickname + "님이 잠들었어요.");
    }

    public NotificationMessage returnTimeChanged(String roommateNickname, LocalTime returnTime) {
        return new NotificationMessage(
                "룸메이트의 귀가 시간이 변경됐어요",
                roommateNickname + "님의 예상 귀가 시간이 " + TIME_FORMAT.format(returnTime) + "으로 변경됐어요."
        );
    }

    public NotificationMessage bedtimeReminder(LocalTime targetBedTime) {
        return new NotificationMessage(
                "취침 시간을 알려드려요",
                "오늘 목표 취침 시간은 " + TIME_FORMAT.format(targetBedTime) + "이에요."
        );
    }

    public record NotificationMessage(String title, String body) {
    }
}
