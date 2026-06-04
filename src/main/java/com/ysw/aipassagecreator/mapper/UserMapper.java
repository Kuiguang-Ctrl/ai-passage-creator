package com.ysw.aipassagecreator.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ysw.aipassagecreator.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

//MyBatis-Flex继承BaseMapper，实现CRUD操作
@Mapper
public interface UserMapper extends BaseMapper<User> {
    /**
     * 原子扣减用户配额
     * 使用 quota > 0 条件确保并发安全，避免超扣
     *
     * @param userId 用户ID
     * @return 影响行数，1表示成功，0表示配额不足
     */
    @Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
    int decrementQuota(@Param("userId") Long userId);
}

