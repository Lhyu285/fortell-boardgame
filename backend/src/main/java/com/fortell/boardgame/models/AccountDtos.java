package com.fortell.boardgame.models;

import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record ProfileResponse(
            String accountId,
            String username,
            String nickname,
            String preferredColor,
            Instant createdAt
    ) {
    }

    public record UpdateProfileRequest(String nickname, String preferredColor) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
    }

    public record DeleteAccountRequest(String password) {
    }
}
