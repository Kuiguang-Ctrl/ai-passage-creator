package com.ysw.aipassagecreator.model.dto.user;

import java.io.Serializable;
import lombok.Data;
//规范接口，用户登录接口，相当于dto
@Data
public class UserLoginRequest implements Serializable {
    private String userAccount;
    private String userPassword;
}
