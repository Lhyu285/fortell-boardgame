package com.fortell.boardgame.services;

import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.AuthDtos;
import com.fortell.boardgame.models.UserAccount;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.repositories.UserRepository;
import com.fortell.boardgame.utils.SessionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class AuthService {
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthDtos.CaptchaResponse issueCaptcha(HttpSession session) {
        StringBuilder builder = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            builder.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        String captcha = builder.toString();
        session.setAttribute(SessionKeys.CAPTCHA, captcha);
        return new AuthDtos.CaptchaResponse(captcha);
    }

    public UserSummary register(AuthDtos.RegisterRequest request, HttpSession session) {
        String username = normalizeUsername(request.username());
        if (username.length() < 3 || username.length() > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名长度需在 3 到 20 之间");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码至少 6 位");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "两次密码不一致");
        }
        Object storedCaptcha = session.getAttribute(SessionKeys.CAPTCHA);
        if (!(storedCaptcha instanceof String captcha) || request.captcha() == null ||
                !captcha.equalsIgnoreCase(request.captcha().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        }

        UserAccount userAccount = userRepository.create(username, passwordEncoder.encode(request.password()));
        loginSession(session, userAccount);
        return new UserSummary(userAccount.id(), userAccount.username());
    }

    public UserSummary login(AuthDtos.LoginRequest request, HttpSession session) {
        String username = normalizeUsername(request.username());
        UserAccount userAccount = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), userAccount.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        loginSession(session, userAccount);
        return new UserSummary(userAccount.id(), userAccount.username());
    }

    public AuthDtos.CurrentUserResponse currentUser(HttpSession session) {
        Object id = session.getAttribute(SessionKeys.USER_ID);
        Object username = session.getAttribute(SessionKeys.USERNAME);
        if (id instanceof Long userId && username instanceof String userName) {
            return new AuthDtos.CurrentUserResponse(true, new UserSummary(userId, userName));
        }
        return new AuthDtos.CurrentUserResponse(false, null);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private void loginSession(HttpSession session, UserAccount userAccount) {
        session.setAttribute(SessionKeys.USER_ID, userAccount.id());
        session.setAttribute(SessionKeys.USERNAME, userAccount.username());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }
}
