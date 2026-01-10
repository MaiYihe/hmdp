package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.converter.UserConverter;
import com.hmdp.converter.UserDTOConverter;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    private final UserConverter userConverter;
    private final UserDTOConverter userDTOConverter;

    @Override
    public Result generateCode(String phone, HttpSession session) {
        // 1. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 2. 保存验证码到 redis // set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 3. 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 4. 返回 ok
        return Result.ok();
    }

    @Override
    @Transactional
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 用手机号作为 Redis key
        String phone = loginForm.getPhone();

        // 1. 校验 Redis 中的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 2. 不一致，报错
            return Result.fail("验证码错误");
        }
        // 3. 一致，根据手机号查询用户
        User user = this.lambdaQuery()
                .eq(User::getPhone, loginForm.getPhone())
                .one();

        // 4. 判断用户是否存在
        if (user == null) {
            // 4.1 不存在，创建新用户并保存
            user = userConverter.toEntity(loginForm);
            this.save(user);
        }

        // 5. 保存用户信息到 redis
        // 5.1 随机生成 token，作为登录令牌 // hutool 无 - 的 UUID
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;
        // 5.2 将 User 对象转为 Hash 存储
        UserDTO userDTO = userDTOConverter.toDTO(user); 
        // 5.3 存储数据到 Redis
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 5.4 设置 token 有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL,TimeUnit.MINUTES);

        // 6. 返回 token 
        return Result.ok(token);
    }

}
