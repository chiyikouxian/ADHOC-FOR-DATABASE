package com.fanet.mapper.pg;

import com.fanet.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("userId") Long userId);

    int insert(User user);
}
