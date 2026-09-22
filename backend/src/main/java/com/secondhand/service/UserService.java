package com.secondhand.service;

import com.secondhand.dto.LoginDTO;
import com.secondhand.dto.RegisterDTO;
import com.secondhand.dto.UpdatePasswordDTO;
import com.secondhand.dto.UpdateProfileDTO;
import com.secondhand.vo.LoginVO;
import com.secondhand.vo.UserVO;

/** 用户模块业务接口 */
public interface UserService {

    /** 注册 */
    UserVO register(RegisterDTO dto);

    /** 登录，成功返回 token + 用户信息 */
    LoginVO login(LoginDTO dto);

    /** 当前登录用户完整信息（个人中心用，含统计） */
    UserVO getCurrentUser();

    /** 修改资料 */
    UserVO updateProfile(UpdateProfileDTO dto);

    /** 修改密码 */
    void updatePassword(UpdatePasswordDTO dto);
}
