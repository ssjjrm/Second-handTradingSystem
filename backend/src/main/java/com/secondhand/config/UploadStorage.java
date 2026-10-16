package com.secondhand.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传文件的存储位置（全项目唯一来源）。
 *
 * <p><b>为什么要单独抽一个类：</b>上传的"写"和访问的"读"必须指向同一个目录。
 * 如果 {@code WebConfig} 和 {@code UploadController} 各自写一遍
 * {@code Paths.get(uploadPath).toAbsolutePath()}，哪天有人改了一处忘了另一处，
 * 就会出现"上传成功但图片 404"这种很难查的问题。
 *
 * <p><b>关于相对路径的坑：</b>配置里 {@code upload.path=uploads} 是相对路径，
 * 它相对于**进程的工作目录**。用 {@code mvn spring-boot:run} 启动时工作目录是
 * {@code backend/}，所以文件落在 {@code backend/uploads/}；
 * 但如果哪天从项目根目录启动，文件就会落到别的地方，而数据库里存的 URL 不变 ——
 * 老图片全部 404。
 *
 * <p>这里用 {@code toAbsolutePath()} 把它**一次性定死**，
 * 并把结果放进日志方便排查（启动时能看到实际目录到底在哪）。
 */
@Getter
@Component
public class UploadStorage {

    /** 磁盘上的真实目录（绝对路径） */
    private final Path rootDir;

    /** 对外访问前缀，默认 /uploads */
    private final String baseUrl;

    public UploadStorage(@Value("${upload.path:uploads}") String uploadPath,
                         @Value("${upload.base-url:/uploads}") String baseUrl) {
        this.rootDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        // 统一成 "/uploads" 这种形式，免得配置里写成 "uploads/" 导致拼出双斜杠
        String normalized = baseUrl.startsWith("/") ? baseUrl : "/" + baseUrl;
        this.baseUrl = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
