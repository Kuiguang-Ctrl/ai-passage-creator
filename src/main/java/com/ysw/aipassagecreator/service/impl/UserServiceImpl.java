package com.ysw.aipassagecreator.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ysw.aipassagecreator.exception.BusinessException;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.mapper.UserMapper;
import com.ysw.aipassagecreator.model.dto.user.UserQueryRequest;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.model.enums.UserRoleEnum;
import com.ysw.aipassagecreator.model.vo.LoginUserVO;
import com.ysw.aipassagecreator.model.vo.UserVO;
import com.ysw.aipassagecreator.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import static com.ysw.aipassagecreator.constant.UserConstant.USER_LOGIN_STATE;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //校验参数
        if(StrUtil.hasBlank(userAccount, userPassword, checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号长度过短");
        }
        if(userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度过短");
        }
        if(!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入密码不一样");
        }
        //查询用户是否以存在
        QueryWrapper queryWrapper = new QueryWrapper();
        //等值查询
        queryWrapper.eq("userAccount",userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号已存在");
        }
        //3加密密码
        String encryptpassword=getEncryptPassword(userPassword);
        //创建用户插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptpassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"注册失败，数据库失败");
        }
        return user.getId();
    }

    /**
     * 用户登录
     * @param userAccount
     * @param userPassword
     * @param request
     * @return
     */
    //用户登录的核心是验证用户身份，然后让服务器记住这个用户，我们把用户信息存入Session，
    // 之后每次请求浏览器都会通过Cookie自动携带SessionID，后端就能识别出当前是那个用户在操作
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //校验参数
        if(StrUtil.hasBlank(userAccount, userPassword)){
            throw new  BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        String encryptpassword=getEncryptPassword(userPassword);
        //3.查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        queryWrapper.eq("userPassword",encryptpassword);
        User user  = this.mapper.selectOneByQuery(queryWrapper);//查询一条数据
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户或者密码不存在");
        }
        //记录用户登录的状态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);//存在seession里卖生成JSESSIONID存在Cookie里面
        return this.getLoginUserVO(user);
    }

    /**
     * 获取登录用户
     * @param request
     * @return
     */
    //虽然Sessio里面保存了用户信息但这些信息可以过时了（比如用户在其他地方修改了昵称或头像）
    //所以我们从Session里面取除用户id,再去数据库里面查一遍最新的数据，确保返回的信息始终都是最新的
        public User getLoginUser(HttpServletRequest request){
            //先判断用户是否登录
            Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
            User currentUser = (User)userObj;
            if(currentUser == null||currentUser.getId() == null){
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            //从数据库查询当前用户信息（保证数据最新）
            Long userId = currentUser.getId();
            currentUser=this.getById(userId);
            if(currentUser == null){
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return currentUser;
        }

    /**
     * 用户注销
     * @param request
     * @return
     */
    //注销就是把Session中保存的信息移除，这样下次请求后端就识别不出用户身份了
    public boolean userLogout(HttpServletRequest request){
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if(userObj == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"用户为登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    //自己写的讲user转成vo
    public LoginUserVO getLoginUserVO(User user){
        LoginUserVO loginUserVO=new LoginUserVO();
        BeanUtils.copyProperties(user,loginUserVO);
        return loginUserVO;
    }
    //使用md5加密算法
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "ysw";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }
    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id) // where id = ${id}
                .eq("userRole", userRole) // and userRole = ${userRole}
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }
}
