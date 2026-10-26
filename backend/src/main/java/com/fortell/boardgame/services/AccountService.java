package com.fortell.boardgame.services;

import com.fortell.boardgame.models.AccountDtos;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.UserAccount;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.repositories.UserRepository;
import com.fortell.boardgame.utils.SessionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AccountService {
    private static final Set<String> PREFERRED_COLORS = Set.of("红", "黄", "蓝", "绿");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AccountDtos.ProfileResponse profile(UserSummary user) {
        return toProfile(findUser(user.id()));
    }

    public AccountDtos.ProfileResponse updateProfile(
            UserSummary user,
            AccountDtos.UpdateProfileRequest request,
            HttpSession session
    ) {
        String nickname = normalizeNickname(request == null ? null : request.nickname());
        String preferredColor = request == null ? "" : normalize(request.preferredColor());
        if (nickname.length() < 1 || nickname.length() > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "昵称长度需在 1 到 20 之间");
        }
        if (!PREFERRED_COLORS.contains(preferredColor)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "偏好颜色不合法");
        }

        userRepository.updateProfile(user.id(), nickname, preferredColor);
        session.setAttribute(SessionKeys.USERNAME, nickname);
        return toProfile(findUser(user.id()));
    }

    public void changePassword(
            UserSummary user,
            AccountDtos.ChangePasswordRequest request,
            HttpSession session
    ) {
        if (request == null || request.currentPassword() == null || request.newPassword() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请完整填写密码信息");
        }
        UserAccount account = findUser(user.id());
        if (!passwordEncoder.matches(request.currentPassword(), account.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }
        if (request.newPassword().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码至少 6 位");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "两次新密码不一致");
        }

        userRepository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        session.invalidate();
    }

    public void deleteAccount(UserSummary user, AccountDtos.DeleteAccountRequest request, HttpSession session) {
        if (request == null || request.password() == null || request.password().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入当前密码确认注销");
        }
        UserAccount account = findUser(user.id());
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }

        userRepository.deleteById(user.id());
        session.invalidate();
    }

    private UserAccount findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号不存在或已注销"));
    }

    private AccountDtos.ProfileResponse toProfile(UserAccount account) {
        return new AccountDtos.ProfileResponse(
                account.accountId(),
                account.username(),
                account.nickname(),
                account.preferredColor(),
                account.createdAt()
        );
    }

    private String normalizeNickname(String nickname) {
        return nickname == null ? "" : nickname.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
