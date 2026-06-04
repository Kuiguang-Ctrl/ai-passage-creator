package com.ysw.aipassagecreator.aop;

import com.ysw.aipassagecreator.annotation.AuthCheck;
import com.ysw.aipassagecreator.exception.BusinessException;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.model.enums.UserRoleEnum;
import com.ysw.aipassagecreator.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * 这段代码是接口权限拦截器：
 * 先获取接口要求的角色和当前登录用户，没登录直接拦截；
 * 如果接口不指定角色，只要登录就放行；
 * 如果接口要求管理员权限，就校验用户是否为管理员，不是则拦截，同时校验用户角色是否合法，非法角色也直接拦截。
 */

/**
 * 权限控制AuthInterceptor切面类
 */
//切面类通过@Around(环绕通知)拦截所有标记@AuthCheck的方法，在方法执行前获取当前登录用户，检查其角色是否满足注解中要求的权限
//如果权限不足，直接抛出异常，请求不会到达Controller
@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;
    @Around("@annotation(authCheck)")
    public Object doIntercept(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        // 2. 获取当前请求的上下文
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        // 3. 从上下文里拿到 HttpServletRequest 对象
        HttpServletRequest request=((ServletRequestAttributes)requestAttributes).getRequest();
        //获取当前登录用户
        //这个是注解传来的角色传来为空
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        //不需要权限直接放行
        if(loginUser==null){
            return joinPoint.proceed();
        }
        //必须要有这个权限才能通过
        //这个是用户的的角色查询数据库得到的
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if(userRoleEnum==null){//判断用户角色是否合法
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //要求必须是管理员权限，但当前用户没有
        //如果接口要求的角色是管理员,但当前登录用户不是管理员抛出异常(注解要求管理员)
        if(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //通过权限校验放行
        return joinPoint.proceed();
    }

}
