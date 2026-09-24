package com.secondhand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.secondhand.common.BusinessException;
import com.secondhand.common.Result;
import com.secondhand.dto.LoginDTO;
import com.secondhand.dto.RegisterDTO;
import com.secondhand.dto.UpdatePasswordDTO;
import com.secondhand.dto.UpdateProfileDTO;
import com.secondhand.entity.User;
import com.secondhand.mapper.UserMapper;
import com.secondhand.service.UserService;
import com.secondhand.util.JwtUtil;
import com.secondhand.util.UserContext;
import com.secondhand.vo.LoginVO;
import com.secondhand.vo.UserStatsVO;
import com.secondhand.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户模块实现。
 *
 * <p>{@code @Transactional} 加在 <b>Service</b> 上，不是 Controller ——
 * Controller 的职责只是接收参数、调用 Service、包装返回，
 * 事务边界应该由业务方法（Service）自己划定。
 * （参考项目把 {@code @Transactional} 标在 Controller 上，属于分层错位。）
 */
@Slf4j
@Service
@RequiredArgsConstructor  //Lombok 为所有 final 字段生成一个构造器
// 写成 final 字段 + @RequiredArgsConstructor 就行了，效果和 @Autowired 一样（都是把对象注入进来）。
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper; // final 字段
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ==================================================================
    //  注册
    // ==================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    // @Transactional：Spring 在调用这个业务方法时开启或加入数据库事务；
    // 方法正常结束就提交，遇到符合回滚规则的异常向外抛出时就回滚。
    // 如果加在类上，login 和 getCurrentUser 也会白白开启事务——每次请求都多一次"开事务/关事务"的开销，
    // 还会占用数据库连接更久。高并发下这些空事务是真实的资源浪费。
    public UserVO register(RegisterDTO dto) {
        // 1. 用户名唯一性检查。（数据库上还有 uk_username 兜底，防并发下的漏网之鱼）
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "用户名已存在");
        }

        // 2. 组装用户对象
        User user = new User();
        user.setUsername(dto.getUsername());
        // ★ 只存密文。encode 内部的随机 salt 保证同一密码两次注册得到不同密文
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 昵称留空就用用户名顶上，避免前端显示空白
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname().trim() : dto.getUsername());
        user.setWechat(blankToNull(dto.getWechat()));
        user.setQq(blankToNull(dto.getQq()));
        user.setPhone(blankToNull(dto.getPhone()));
        // 角色固定 user —— 绝不能让前端传 role，否则谁都能把自己注册成管理员
        user.setRole("user");
        user.setStatus(1);

        userMapper.insert(user);
        log.info("新用户注册：id={}, username={}", user.getId(), user.getUsername());

        return toVO(user);
    }

    // ==================================================================
    //  登录
    // ==================================================================
    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));

        // "用户不存在"和"密码错误"故意返回同一句提示：
        // 否则别人可以靠错误信息的不同，把系统里有哪些账号一个个试出来
        if (user == null) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录：id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());

        // 登录只返回前端立刻要用的几个字段（完整资料走 /api/auth/me）
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setHasContact(hasContact(user));

        return new LoginVO(token, vo);
    }

    // ==================================================================
    //  当前用户信息
    // ==================================================================
    @Override
    public UserVO getCurrentUser() {
        Integer userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "用户不存在");
        }

        UserVO vo = toVO(user);
        // TODO(S8)：接入 product / favorite / user_behavior 的真实统计
        vo.setStats(UserStatsVO.empty());
        return vo;
    }

    // ==================================================================
    //  修改资料
    // ==================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileDTO dto) {
        Integer userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "用户不存在");
        }

        // 传了就用新值（空串 = 清空），没传就保持原值
        // 传了->空串"" , 没传->null
        //
        // ⚠️ 昵称是唯一的例外：它必填、**不允许清空**。
        //    昵称是展示给别人的名字，空了会让商品列表、卖家主页显示成空白；
        //    而且 UserVO 上的 @JsonInclude(NON_NULL) 会让这个键直接从 JSON 里消失，
        //    前端连"这里本该有值"都看不出来。（DTO 上也有 @Size(min=1) 兜一层）
        String nickname = user.getNickname();
        if (dto.getNickname() != null) {
            String trimmedNickname = dto.getNickname().trim();
            if (!StringUtils.hasText(trimmedNickname)) {
                throw new BusinessException(Result.CODE_BAD_REQUEST, "昵称不能为空");
            }
            nickname = trimmedNickname;
        }

        String wechat = dto.getWechat() != null ? blankToNull(dto.getWechat()) : user.getWechat();
        String qq = dto.getQq() != null ? blankToNull(dto.getQq()) : user.getQq();
        String phone = dto.getPhone() != null ? blankToNull(dto.getPhone()) : user.getPhone();
        String avatar = dto.getAvatar() != null ? blankToNull(dto.getAvatar()) : user.getAvatar();

        // 业务规则 BR-U：微信和 QQ 不能同时为空，否则买家点了"获取联系方式"什么也拿不到
        if (!StringUtils.hasText(wechat) && !StringUtils.hasText(qq)) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "至少保留一种联系方式，便于买家联系你");
        }

        // ⚠️ 这里不能用 updateById：
        //    它默认跳过 null 字段，就没法把 wechat 从"有值"改回"没有"。
        //    LambdaUpdateWrapper 的 set() 会明确生成 SET wechat = NULL。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getNickname, nickname)
                .set(User::getWechat, wechat)
                .set(User::getQq, qq)
                .set(User::getPhone, phone)
                .set(User::getAvatar, avatar));

        return toVO(userMapper.selectById(userId));
    }

    // ==================================================================
    //  修改密码
    // ==================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UpdatePasswordDTO dto) {
        Integer userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "原密码错误");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "新密码不能与原密码相同");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        log.info("用户 {}（id={}）修改了密码", user.getUsername(), userId);
    }

    // ==================================================================
    //  内部工具
    // ==================================================================

    /** 实体 → 出参。手机上标了 @JsonIgnore 不会丢，这里本来就绕开实体直接给 VO */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setWechat(user.getWechat());
        vo.setQq(user.getQq());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setHasContact(hasContact(user));
        return vo;
    }

    /** 是否已填至少一种联系方式（微信号或 QQ） */
    private boolean hasContact(User user) {
        return StringUtils.hasText(user.getWechat()) || StringUtils.hasText(user.getQq());
    }

    /** 空白字符串统一转成 null，避免库里出现 "" 和 NULL 两种"空" */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
