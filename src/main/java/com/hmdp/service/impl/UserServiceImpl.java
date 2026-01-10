package com.hmdp.service.impl;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.converter.UserConverter;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;

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

    @Resource StringRedisTemplate stringRedisTemplate;
    private final UserConverter userConverter;

    @Override
    public Result generateCode(String phone, HttpSession session) {
        // 1. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 2. 保存验证码到 session
        session.setAttribute("code", code);
        // 3. 发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        // 4. 返回 ok
        return Result.ok();
    }

    @Override
    @Transactional
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)){
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
        // 5. 保存用户信息到 session
        session.setAttribute("user", user);
        return Result.ok();
    }
    
}
