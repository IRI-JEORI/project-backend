package com.nunnun.user.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserWriteGuard {

    private final UserRepository users;

    public UserWriteGuard(UserRepository users) {
        this.users = users;
    }

    public User lockActive(Long userId) {
        return users.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public Optional<User> lockIfActive(Long userId) {
        return users.findActiveByIdForUpdate(userId);
    }

    public Map<Long, User> lockActiveInOrder(Collection<Long> userIds) {
        List<Long> orderedIds = userIds.stream().distinct().sorted().toList();
        Map<Long, User> lockedUsers = new LinkedHashMap<>();
        for (Long userId : orderedIds) {
            User user = users.findActiveByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            lockedUsers.put(userId, user);
        }
        return lockedUsers;
    }

    public Map<Long, User> lockRequiredActiveWithParticipants(Long requiredUserId, Collection<Long> userIds) {
        List<Long> orderedIds = userIds.stream().distinct().sorted().toList();
        Map<Long, User> lockedUsers = new LinkedHashMap<>();
        for (Long userId : orderedIds) {
            users.findByIdForUpdate(userId).ifPresent(user -> lockedUsers.put(userId, user));
        }
        User requiredUser = lockedUsers.get(requiredUserId);
        if (requiredUser == null || requiredUser.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return lockedUsers;
    }
}
