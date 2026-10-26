package com.fortell.boardgame.controllers;

import com.fortell.boardgame.models.AccountDtos;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.security.AuthSupport;
import com.fortell.boardgame.services.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;
    private final AuthSupport authSupport;

    public AccountController(AccountService accountService, AuthSupport authSupport) {
        this.accountService = accountService;
        this.authSupport = authSupport;
    }

    @GetMapping
    public AccountDtos.ProfileResponse profile(HttpSession session) {
        return accountService.profile(authSupport.requireUser(session));
    }

    @PutMapping("/profile")
    public AccountDtos.ProfileResponse updateProfile(
            @RequestBody AccountDtos.UpdateProfileRequest request,
            HttpSession session
    ) {
        return accountService.updateProfile(authSupport.requireUser(session), request, session);
    }

    @PostMapping("/password")
    public RoomDtos.ApiMessage changePassword(
            @RequestBody AccountDtos.ChangePasswordRequest request,
            HttpSession session
    ) {
        accountService.changePassword(authSupport.requireUser(session), request, session);
        return new RoomDtos.ApiMessage(true, "密码已修改，请使用新密码重新登录");
    }

    @DeleteMapping
    public RoomDtos.ApiMessage deleteAccount(
            @RequestBody AccountDtos.DeleteAccountRequest request,
            HttpSession session
    ) {
        accountService.deleteAccount(authSupport.requireUser(session), request, session);
        return new RoomDtos.ApiMessage(true, "账号已注销");
    }
}
