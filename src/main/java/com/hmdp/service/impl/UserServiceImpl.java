package com.hmdp.service.impl;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource StringRedisTemplate stringRedisTemplate;

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

    
}
