package com.hmdp.interceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.util.StrUtil;

public class LoginIntercaptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginIntercaptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");

        if (StrUtil.isBlank(token)) {
            // 不存在，拦截，返回 401
            response.setStatus(401);
            return false;
        }

        // 2. 基于 Token 获取 Redis 中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3. 将查询到的 Hash 数据转为 UserDTO 对象 // 取出数据以后反序列化
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf((String) userMap.get("id")));
        userDTO.setNickName((String) userMap.get("nickName"));
        userDTO.setIcon((String) userMap.get("icon"));

        // 4. 将用户信息保存到 ThreadLocal
        UserHolder.saveUser(userDTO);
        // 5. 刷新 token 的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserHolder.removeUser();
    }

}
