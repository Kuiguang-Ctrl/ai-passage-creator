package com.ysw.aipassagecreator.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "user", camelToUnderline = false)//@table指定数据库表名，false表示不转换为下划线命名法
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id（使用雪花算法生成）
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String userAccount;
    private String userPassword;
    private String userName;
    private String userAvatar;//用户头像的 URL
    private String userProfile;//用户个人简介
    private String userRole;//用户角色
    private LocalDateTime editTime;//最后一次编辑信息的时间
    private LocalDateTime createTime;//用户创建时间
    private LocalDateTime updateTime;//用户信息最后更新时间

    /**
     * 逻辑删除
     */
    @Column(isLogicDelete = true)//@column指定数据库字段名，true表示逻辑删除
    private Integer isDelete;
    /**
     * 剩余配额
     */
    private Integer quota;
    /**
     * 成为会员时间
     */
    private LocalDateTime vipTime;

}
