package com.friendsfantasy.fantasybackend.controller;

import com.friendsfantasy.fantasybackend.dto.ApiResponse;
import com.friendsfantasy.fantasybackend.dto.LoginRequest;
import com.friendsfantasy.fantasybackend.dto.RegisterRequest;
import com.friendsfantasy.fantasybackend.entity.User;
import com.friendsfantasy.fantasybackend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        user.setPassword(null);
        return ApiResponse.success("Registered successfully", user);
    }

    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request);
        user.setPassword(null);
        return ApiResponse.success("Login successful", user);
    }
}