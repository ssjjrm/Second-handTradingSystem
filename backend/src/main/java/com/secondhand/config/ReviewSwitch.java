package com.secondhand.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 「发布商品是否需要审核」的开关。
 *
 * <h3>为什么要有这个开关</h3>
 *
 * <p>正式流程是「发布 → 待审核 → 管理员通过 → 才出现在前台列表」。
 * 这个流程本身没问题，但**开发和演示时非常碍事**：
 * 想看收藏 / 咨询 / 卖家主页这些功能，就得先发一件商品、再切管理员账号审核一次，
 * 每换一件测试数据都要重复一遍。
 *
 * <p>所以加一个开关：
 * <pre>
 *   true  （默认）发布后 status = 0 待审核 —— 正式行为
 *   false         发布后 status = 1 在售   —— 仅测试/演示用，发布即可见
 * </pre>
 *
 * <h3>两个来源，优先级</h3>
 *
 * <ol>
 *   <li><b>启动配置</b>：`application.yml` 里的 {@code app.review.enabled}，
 *       也可以用环境变量 {@code REVIEW_ENABLED=false} 覆盖（不改代码、不改配置文件）</li>
 *   <li><b>运行时开关</b>：{@code PUT /api/admin/settings/review?enabled=false}，
 *       管理员在后台点一下即可切换，<b>不用重启后端</b></li>
 * </ol>
 *
 * <p>运行时的值只存在内存里，重启后回到配置文件的值 —— 这是刻意的：
 * 免得某次调试忘了关，结果下次启动还以为自己在"正式模式"。
 *
 * <h3>为什么字段是 volatile</h3>
 *
 * <p>Tomcat 是多个线程同时处理请求的，这个布尔值会被其中一个线程改、
 * 被另外几十个线程读。{@code volatile} 保证"改了立刻对别的线程可见"
 * （否则可能读到 CPU 缓存里的旧值，表现为"点了开关没反应"）。
 * 单个 boolean 的读写本身就是原子的，不需要更重的锁。
 */
@Slf4j
@Component
public class ReviewSwitch {

    /** 配置文件里的值，重启后回到它 */
    private final boolean configValue;

    /** 当前生效值（可被后台接口在运行时修改） */
    private volatile boolean enabled;

    public ReviewSwitch(@Value("${app.review.enabled:true}") boolean enabled) {
        this.configValue = enabled;
        this.enabled = enabled;
    }

    /**
     * 启动时把当前模式打到日志里。
     *
     * <p>⚠️ 审核关闭时必须**显眼地**提示一句 —— 否则很容易出现
     * "演示时忘了自己关过审核，还奇怪为什么商品一发就在列表里"。
     */
    @PostConstruct
    public void logStartupMode() {
        if (enabled) {
            log.info("商品发布需要审核（正式模式）：新发布的商品会进入待审核，管理员通过后才上架");
        } else {
            log.warn("========================================================");
            log.warn(" ⚠️  商品审核已关闭（测试模式）");
            log.warn("    新发布的商品会【直接上架】，不再经过管理员审核。");
            log.warn("    如需恢复正式流程：把 application.yml 的 app.review.enabled 改回 true，");
            log.warn("    或调用 PUT /api/admin/settings/review?enabled=true 在线打开。");
            log.warn("========================================================");
        }
    }

    /** 是否需要审核 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 配置文件里的默认值（后台页面用来提示"重启后会回到哪个值"） */
    public boolean getConfigValue() {
        return configValue;
    }

    /**
     * 运行时切换。
     *
     * @return 切换前的旧值（方便调用方打日志"从 x 改成 y"）
     */
    public boolean setEnabled(boolean value) {
        boolean old = this.enabled;
        this.enabled = value;
        if (old != value) {
            log.warn("商品审核开关被改为 {}（{}）", value ? "开启" : "关闭",
                    value ? "发布后进入待审核" : "⚠️ 发布即上架，仅测试用");
        }
        return old;
    }
}
