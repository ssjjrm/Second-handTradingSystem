package com.secondhand.controller;

import com.secondhand.common.Result;
import com.secondhand.dto.LoginDTO;
import com.secondhand.dto.RegisterDTO;
import com.secondhand.dto.UpdatePasswordDTO;
import com.secondhand.dto.UpdateProfileDTO;
import com.secondhand.service.UserService;
import com.secondhand.vo.LoginVO;
import com.secondhand.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证模块接口（接口文档 §3）。
 *
 * <p>Controller 只做三件事：收参数、调 Service、包 Result。
 * 业务规则和事务都在 Service 里。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 3.1 注册（公开） */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    /** 3.2 登录（公开） */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /** 3.3 当前用户信息（需登录） */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }

    /** 3.4 修改资料（需登录） */
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        UserVO vo = userService.updateProfile(dto);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hasContact", vo.getHasContact());
        return Result.success("修改成功", data);
    }

    /** 3.5 修改密码（需登录） */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(dto);
        return Result.success("密码修改成功", null);
    }
}
