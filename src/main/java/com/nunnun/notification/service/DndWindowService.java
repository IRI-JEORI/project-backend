package com.nunnun.notification.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.entity.DndWindow;
import com.nunnun.notification.repository.DndWindowRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DndWindowService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final DndWindowRepository dndWindows;
    private final UserRepository users;
    private final UserWriteGuard userWriteGuard;
    private final DndWindowParser parser;

    public DndWindowService(
            DndWindowRepository dndWindows,
            UserRepository users,
            UserWriteGuard userWriteGuard,
            DndWindowParser parser
    ) {
        this.dndWindows = dndWindows;
        this.users = users;
        this.userWriteGuard = userWriteGuard;
        this.parser = parser;
    }

    @Transactional(readOnly = true)
    public List<DndWindow> getDndWindows(Long userId) {
        findActiveUser(userId);
        return dndWindows.findAllByUserId(userId).stream()
                .sorted(Comparator
                        .comparingInt((DndWindow window) -> window.getDayOfWeek().getValue())
                        .thenComparing(DndWindow::getStartTime)
                        .thenComparing(DndWindow::getEndTime)
                        .thenComparing(DndWindow::getId))
                .toList();
    }

    @Transactional
    public DndWindow createDndWindow(Long userId, String text) {
        User user = userWriteGuard.lockActive(userId);
        ParsedDndWindow parsed = parser.parse(text);

        if (dndWindows.existsByUserIdAndDayOfWeekAndStartTimeAndEndTime(
                userId,
                parsed.dayOfWeek(),
                parsed.startTime(),
                parsed.endTime()
        )) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        try {
            return dndWindows.saveAndFlush(DndWindow.create(
                    user,
                    parsed.dayOfWeek(),
                    parsed.startTime(),
                    parsed.endTime()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @Transactional
    public void deleteDndWindow(Long userId, Long dndWindowId) {
        userWriteGuard.lockActive(userId);
        DndWindow dndWindow = dndWindows.findByIdAndUserId(dndWindowId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        dndWindows.delete(dndWindow);
    }

    @Transactional(readOnly = true)
    public boolean isDndActive(Long userId, ZonedDateTime now) {
        ZonedDateTime seoulNow = now.withZoneSameInstant(BUSINESS_ZONE);
        return dndWindows.findAllByUserIdAndDayOfWeek(
                        userId,
                        seoulNow.getDayOfWeek()
                ).stream()
                .anyMatch(window ->
                        !seoulNow.toLocalTime().isBefore(window.getStartTime())
                                && seoulNow.toLocalTime().isBefore(window.getEndTime())
                );
    }

    @Transactional(readOnly = true)
    public Set<Long> findDndActiveUserIds(Collection<Long> userIds, ZonedDateTime now) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        ZonedDateTime seoulNow = now.withZoneSameInstant(BUSINESS_ZONE);
        return dndWindows.findAllByUserIdInAndDayOfWeek(userIds, seoulNow.getDayOfWeek()).stream()
                .filter(window ->
                        !seoulNow.toLocalTime().isBefore(window.getStartTime())
                                && seoulNow.toLocalTime().isBefore(window.getEndTime())
                )
                .map(window -> window.getUser().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    private User findActiveUser(Long userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
