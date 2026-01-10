package com.hmdp.converter;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserDTOConverter {
    
    UserDTO toDTO(User user);
}
