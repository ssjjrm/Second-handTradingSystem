package com.secondhand.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 图片上传出参 */
@Data
@AllArgsConstructor
public class UploadVO {

    /** 可直接访问的相对 URL，如 {@code /uploads/20260924/abc123.jpg} */
    private String url;
}
