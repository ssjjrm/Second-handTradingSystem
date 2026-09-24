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

// GET 查，POST 提交/创建（创建资源、登录、提交表单），PUT 替换/更新。
// GET 可缓存，POST、PUT一般不缓存
// 部分更新用 PATCH
// 删除用 DELETE

// Query 是 URL 里的查询参数；Body 是 HTTP 请求体里的数据。
// HTTP 请求 包含：
// 请求行 POST /api/users?from=web HTTP/1.1  -> Query
// 请求头	Host、Content-Type、Content-Length 等
// 空行	分隔头和体
// 请求体	{"username":"zhangsan","password":"123456"}  -> Body

/**
 * 认证模块接口（接口文档 §3）。
 *
 * <p>Controller 只做三件事：收参数、调 Service、包 Result。
 * 业务规则和事务都在 Service 里。
 */
@RestController
// @Controller：告诉 Spring 这是一个控制器，负责接收 Web 请求。
// @ResponseBody：该类的所有方法返回值，直接作为 HTTP 响应体
@RequestMapping("/api/auth")
// 类级别的前缀，AuthController 用它写 /api/auth，方法上就不用重复写
@RequiredArgsConstructor
// 会自动生成一个包含所有 final 字段的构造方法。
// 也就是说——只要你把依赖写成 final，Spring Boot 就能自动注入。
public class AuthController {

    private final UserService userService;  // 写成final 字段

    /** 3.1 注册（公开） */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

//    @RequestBody：把 HTTP 请求体（JSON）反序列化成 Java 对象。
//    @Valid：告诉 Spring 对这个对象执行 Bean Validation 校验（@NotNull、@Size、@Email 等）。
//    合在一起：先绑定 JSON 到 DTO，再自动校验 DTO 字段；校验不通过，方法体不会执行，直接抛异常。
//    有校验失败 → 抛出 MethodArgumentNotValidException
//    那抛出的异常去哪了？GlobalExceptionHandler 里有handleValidException专门的方法接它：

//    @Valid 的作用是：
//    在进入你的方法之前，让 Spring 先检查这个对象上的所有校验注解。
//    全部通过才执行方法；有一条不通过，你的方法根本不执行，直接返回错误。
//    把校验规则"声明"在 DTO 上。规则全部集中在字段上，一行业务代码都没有。
//    Controller 里没有任何校验代码——就一个 @Valid

//    为什么用 DTO 而不是直接收 Emp
//    RegisterDTO 没有 role 字段 → 前端想传都传不进来
//    DTO某个接口的入参 ；VO某个接口的出参


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
