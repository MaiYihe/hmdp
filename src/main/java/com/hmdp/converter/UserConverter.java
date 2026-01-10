package com.hmdp.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.SystemConstants;

import cn.hutool.core.util.RandomUtil;;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "icon", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "nickName", expression = "java(generateNickName())")
    User toEntity(LoginFormDTO dto);

    default String generateNickName() {
        return SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10);
    }
}
