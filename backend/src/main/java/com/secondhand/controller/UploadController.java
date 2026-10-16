package com.secondhand.controller;

import com.secondhand.common.BusinessException;
import com.secondhand.common.Result;
import com.secondhand.config.UploadStorage;
import com.secondhand.vo.UploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传（接口文档 §9.1）。
 *
 * <p>存本地磁盘：{@code uploads/yyyyMMdd/uuid.ext}，数据库里只存 URL。
 *
 * <p><b>两个安全细节：</b>
 * <ol>
 *   <li><b>不用原始文件名存盘</b>，而是生成 UUID。
 *       原始文件名可能是 {@code ../../application.yml} 这种恶意路径，
 *       直接拼路径会让攻击者把文件写到项目外面去。
 *       用 UUID 之后，文件名完全由服务端控制，这条攻击路径就没了。</li>
 *   <li><b>类型校验用 MIME + 扩展名双重白名单</b>，
 *       而不是"黑名单排除 exe"。黑名单永远列不全，白名单列不全也只是少支持一种格式。</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    /** 允许的图片 MIME 类型 */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /** 允许的扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 单张上限 5MB（和 application.yml 的 multipart 限制保持一致） */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final UploadStorage uploadStorage;

    @PostMapping("/image")
    public Result<UploadVO> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "请选择要上传的图片");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "只能上传图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "图片大小不能超过5MB");
        }

        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path dir = uploadStorage.getRootDir().resolve(dateDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);

        // 用 Files.copy 而不是 MultipartFile.transferTo：
        // transferTo 的参数是 File 类型，且在目标路径已存在时行为不一致。
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = uploadStorage.getBaseUrl() + "/" + dateDir + "/" + filename;
        log.info("图片上传成功：{}（{} 字节）", url, file.getSize());
        return Result.success("上传成功", new UploadVO(url));
    }

    /**
     * 决定用什么扩展名存盘。
     *
     * <p>优先用原始文件名的扩展名（在白名单内的才行），
     * 拿不到就按 MIME 类型兜底。反正文件名会用 UUID 重写，
     * 这里只是为了让文件"看起来像个图片"，方便人工排查。
     */
    private String resolveExtension(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot + 1).toLowerCase();
                if (ALLOWED_EXTENSIONS.contains(ext)) {
                    return ext;
                }
            }
        }
        return switch (contentType == null ? "" : contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
