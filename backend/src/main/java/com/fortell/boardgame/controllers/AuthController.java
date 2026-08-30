package com.fortell.boardgame.controllers;

import com.fortell.boardgame.models.AuthDtos;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.services.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/captcha")
    public AuthDtos.CaptchaResponse captcha(HttpSession session) {
        return authService.issueCaptcha(session);
    }

    @PostMapping("/register")
    public AuthDtos.CurrentUserResponse register(@RequestBody AuthDtos.RegisterRequest request, HttpSession session) {
        return new AuthDtos.CurrentUserResponse(true, authService.register(request, session));
    }

    @PostMapping("/login")
    public AuthDtos.CurrentUserResponse login(@RequestBody AuthDtos.LoginRequest request, HttpSession session) {
        return new AuthDtos.CurrentUserResponse(true, authService.login(request, session));
    }

    @PostMapping("/logout")
    public RoomDtos.ApiMessage logout(HttpSession session) {
        authService.logout(session);
        return new RoomDtos.ApiMessage(true, "已退出登录");
    }

    @GetMapping("/me")
    public AuthDtos.CurrentUserResponse me(HttpSession session) {
        return authService.currentUser(session);
    }
}
