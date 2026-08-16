package com.nunnun.notification.service;

import java.time.Duration;
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
                "룸메이트 귀가 시간이 변경됐어요",
                roommateNickname + "님의 예상 귀가 시간이 " + TIME_FORMAT.format(returnTime) + "로 변경됐어요."
        );
    }

    public NotificationMessage bedtimeReminder(LocalTime targetBedTime) {
        return new NotificationMessage(
                "취침 시간이 다가와요",
                "오늘 목표 취침 시간은 " + TIME_FORMAT.format(targetBedTime) + "이에요."
        );
    }

    public NotificationMessage bedtimeReminder(Duration remainingToWake) {
        long totalMinutes = remainingToWake.toMinutes();
        if (totalMinutes == 540) {
            return new NotificationMessage("취침 시간이 다가와요", "취침 1시간 전이에요.");
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        String remainingText = minutes == 0
                ? hours + "시간"
                : hours + "시간 " + minutes + "분";
        return new NotificationMessage("취침 시간이 다가와요", "기상까지 " + remainingText + " 남았어요.");
    }

    public record NotificationMessage(String title, String body) {
    }
}
