package com.ysw.aipassagecreator.controller;

import com.ysw.aipassagecreator.annotation.AuthCheck;
import com.ysw.aipassagecreator.common.BaseResponse;
import com.ysw.aipassagecreator.common.ResultUtils;
import com.ysw.aipassagecreator.constant.UserConstant;
import com.ysw.aipassagecreator.exception.BusinessException;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.model.entity.PaymentRecord;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.service.PaymentService;
import com.ysw.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/payment")
@Slf4j
@Tag(name="paymentContorller",description = "支付接口")
public class PaymentContorller {
    @Resource
    private PaymentService paymentService;
    @Resource
    private UserService userService;
    /**
     * 创建VIP支付会话
      */
    @PostMapping("/create-vip-session")
    @Operation(summary = "创建VIP支付会话")
    public BaseResponse<String> createVipPaymentSession(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        try {
            String sessionUrl=paymentService.createVipPaymentSession(loginUser.getId());
            return ResultUtils.success(sessionUrl);
        }catch (BusinessException e){
            throw e;
        }catch (Exception e){
            log.error("创建会话失败",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"创建支付会话失败");
        }
    }
    /**
     * 申请退款
     */
    //(required = false)表示可传可不传
    @PostMapping("/refund")
    @Operation(summary = "申请退款")
    @AuthCheck(mustRole = UserConstant.VIP_ROLE)
    public BaseResponse<Boolean> refund(@RequestParam(required = false) String reason,HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        try {
            boolean success= paymentService.handleRefund(loginUser.getId(),reason);
            return ResultUtils.success(success);
        }catch (BusinessException e){
            throw e;
        }catch (Exception e){
            log.error("退款失败",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"退款失败");
        }
    }
    /**
     * 获取当前用户支付记录
     */
    @GetMapping("/records")
    @Operation(summary = "获取当前用户支付记录")
    public BaseResponse<List<PaymentRecord>> getPaymentRecords(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        List<PaymentRecord> records = paymentService.getPaymentRecords(loginUser.getId());
        return ResultUtils.success(records);
    }


}
