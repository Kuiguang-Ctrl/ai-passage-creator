package com.ysw.aipassagecreator.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
//登录试图(VO),用于脱敏后返回给前端，不会包含密码等敏感字段
@Data
public class LoginUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime vipTime;

}
