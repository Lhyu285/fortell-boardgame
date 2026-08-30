package com.fortell.boardgame.models;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record CaptchaResponse(String captcha) {
    }

    public record RegisterRequest(String username, String password, String confirmPassword, String captcha) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record CurrentUserResponse(boolean authenticated, UserSummary user) {
    }
}
