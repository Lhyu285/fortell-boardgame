package com.fortell.boardgame.security;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.utils.SessionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthSupport {

    public UserSummary requireUser(HttpSession session) {
        Object id = session.getAttribute(SessionKeys.USER_ID);
        Object username = session.getAttribute(SessionKeys.USERNAME);
        if (!(id instanceof Long userId) || !(username instanceof String userName)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return new UserSummary(userId, userName);
    }

    public UserSummary optionalUser(HttpSession session) {
        Object id = session.getAttribute(SessionKeys.USER_ID);
        Object username = session.getAttribute(SessionKeys.USERNAME);
        if (id instanceof Long userId && username instanceof String userName) {
            return new UserSummary(userId, userName);
        }
        return null;
    }
}
