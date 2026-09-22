package com.secondhand.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.secondhand.common.Result;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <p>S2 阶段唯一的接口，用来证明三件事：
 * <ol>
 *   <li>Spring Boot 起得来，端口 8081</li>
 *   <li>连得上 secondhand_shop 数据库</li>
 *   <li><b>分页插件真的生效</b>（否则 selectPage 会静默返回全表）</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final UserMapper userMapper;

    /** 活着吗 —— 不碰数据库 */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "secondhand-backend");
        data.put("port", 8081);
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success("服务正常", data);
    }

    /**
     * 数据库连通性 + 分页插件自检。
     *
     * <p>这里故意按 size=2 查询用户表：
     * <ul>
     *   <li>{@code total} 是真实总条数（6）</li>
     *   <li>{@code pageSize} 应该等于 2</li>
     *   <li>{@code records} 数组长度应该等于 2</li>
     * </ul>
     * 如果 {@code records} 里出现了 6 条，说明分页插件没注册成功。
     */
    @GetMapping("/db")
    public Result<Map<String, Object>> db() {
        long total = userMapper.selectCount(null);

        IPage<User> page = userMapper.selectPage(new Page<>(1, 2), null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("database", "secondhand_shop");
        data.put("userTotal", total);
        data.put("pageNo", page.getCurrent());
        data.put("pageSize", page.getSize());
        data.put("pages", page.getPages());
        data.put("recordCount", page.getRecords().size());
        data.put("records", page.getRecords());
        data.put("paginationWorking", page.getRecords().size() == 2);
        return Result.success("数据库连通，分页插件生效", data);
    }
}
