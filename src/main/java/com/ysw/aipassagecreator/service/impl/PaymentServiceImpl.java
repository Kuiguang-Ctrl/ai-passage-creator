package com.ysw.aipassagecreator.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.exception.StripeException;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.ysw.aipassagecreator.config.StripeConfig;
import com.ysw.aipassagecreator.constant.UserConstant;
import com.ysw.aipassagecreator.exception.BusinessException;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.mapper.PaymentRecordMapper;
import com.ysw.aipassagecreator.mapper.UserMapper;
import com.ysw.aipassagecreator.model.entity.PaymentRecord;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.model.enums.PaymentStatusEnum;
import com.ysw.aipassagecreator.model.enums.ProductTypeEnum;
import com.ysw.aipassagecreator.service.PaymentService;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.session.SessionProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付服务的实现
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    //代表美元以美元为货币单位
    private static final String CURRENCY_USD = "USD";
    // 代表 1美元 = 100 美分
    private static final Long CENTS_MULTRLTER=100L;
    @Resource
    private StripeConfig stripeConfig;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PaymentRecordMapper paymentRecordMapper;

    @Override
    public String createVipPaymentSession(Long userId) throws StripeException {
        User user=getUserOrThrow(userId);//获取用户
        validateNotVip(user);//验证用户不是vip（如果是vip会包错误信息）
        ProductTypeEnum productType = ProductTypeEnum.VIP_PERMANENT;
        Session session=createStripeSession(userId,productType);
        savePaymentRecord(userId,session,productType);

        log.info("创建支付会话成功，userId={},session={}",userId,session);
        return session.getUrl();//支付的页面地址
    }
    /**
     * 处理支付成功回调
     *
     * @param session Stripe Checkout Session
     */
    @SneakyThrows
    @Override
    @Transactional(rollbackFor = StripeException.class)//保证原子性
    public void handlePaymentSuccess(Session session) {
        String sessionId = session.getId();
        String userId=session.getMetadata().get("userId");
        String paymentSessionId=session.getPaymentIntent();//这是 Stripe 底层真正扣款的交易号


        PaymentRecord record=findPaymentRecordBySessionId(sessionId);//根据sessionId查询支付订单
        if(record==null){
            log.warn("支付订单不存在，sessionId={},",sessionId);
            return;
        }

        //幂等性检查
        if(PaymentStatusEnum.SUCCEEDED.equals(record.getStatus())){
            log.info("支付记录已处理，sessionId={},",sessionId);
            return;
        }

        updataPaymentStatus(record.getId(),PaymentStatusEnum.SUCCEEDED,paymentSessionId);
        upgradeUserToVip(Long.valueOf(userId));

        log.info("支付成功，用户已升级为VIP,userId={},sessionId={}",userId,sessionId);
    }
    /**
     * 处理退款
     *
     * @param userId 用户ID
     * @param reason 退款原因
     * @return 是否退款成功
     */
    @Override
    public boolean handleRefund(Long userId, String reason) throws StripeException {
        User user=getUserOrThrow(userId);
        validateIsVip(user);

        PaymentRecord paymentRecord=findLatestSuccessfulPayment(userId);
        if(paymentRecord==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"未找到支付记录");
        }
        if(paymentRecord.getStripePaymentIntentId()==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"支付记录无效");
        }
        Refund refund=createStripeRefund(paymentRecord.getStripePaymentIntentId());//创建Stripe退款
        if(!"success".equals(refund.getStatus())){
            return false;
        }
        updataRefundRecord(paymentRecord.getId(),reason);
        revokeVipStatus(userId);

        log.info("退款成功，已取消VIP身份，userId={},refundId={}",userId,refund.getId());
        return true;
    }
    /**
     * 验证 Webhook 签名
     *
     * @param payload 请求体
     * @param sigHeader 签名头
     * @return Stripe Event
     */
    @Override
    public Event constructEvent(String payload, String sigHeader) throws Exception {
        return Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
    }

    /**
     * 获取用户支付记录
     *
     * @param userId 用户ID
     * @return 支付记录列表
     */
    @Override
    public List<PaymentRecord> getPaymentRecords(Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("createTime", false);
        return paymentRecordMapper.selectListByQuery(queryWrapper);
    }

    /**
     * 创建stripe支付会话
     */
    private Session createStripeSession(Long userId,ProductTypeEnum productType) throws StripeException {
        //.multiply(...)相乘
        //把商品的美元价格 → 精确计算 → 转成 Stripe 需要的美分整数
        long amountCents=productType.getPrice().multiply(new BigDecimal(CENTS_MULTRLTER)).longValue();
       SessionCreateParams params= SessionCreateParams.builder()
               .setMode(SessionCreateParams.Mode.PAYMENT)            //(支付类型)
               .setSuccessUrl(stripeConfig.getSuccessUrl())          //(成功跳转地址)
               .addLineItem(buildLineItem(productType,amountCents))  //(商品明细)
               .putMetadata("userId",String.valueOf(userId))
               .putMetadata(("productType"),productType.getValue())
               .build();
        return Session.create(params);
    }
    /**
     * 构建支付行项目
     */
    private SessionCreateParams.LineItem buildLineItem(ProductTypeEnum productType, long amountInCents) {
        return SessionCreateParams.LineItem.builder()
                .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(CURRENCY_USD)
                                .setUnitAmount(amountInCents)
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(productType.getDescription())
                                                .setDescription("解锁全部高级功能，无限创作配额，终身有效")
                                                .build()
                                )
                                .build()
                )
                .setQuantity(1L)
                .build();
    }

    /**
     * 获取用户或者抛出异常
     */
    private User getUserOrThrow(Long userId){
        User user = userMapper.selectOneById(userId);
        if(user==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"用户不存在");
        }
        return user;
    }
    /**
     * 验证用户不是vip(----)
     */
    private void validateNotVip(User user){
        if(UserConstant.VIP_ROLE.equals(user.getUserRole())){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"您以是永久会员");
        }
    }

    /**
     * 验证用户是VIP(是vip通过不是VIP报错)
     * @param user
     */
    private void validateIsVip(User user){
        if(!UserConstant.VIP_ROLE.equals(user.getUserRole())){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"您不是会员，无法退款");
        }
    }
    /**
     * 保存支付记录
     */
    private void savePaymentRecord(Long userId,Session session,ProductTypeEnum productType) throws StripeException {
        PaymentRecord record=PaymentRecord.builder()
                .userId(userId)
                .stripeSessionId(session.getId())
                .amount(productType.getPrice())
                .currency(CURRENCY_USD)
                .status(PaymentStatusEnum.PENDING.getValue())
                .productType(productType.getValue())
                .description(productType.getDescription())
                .build();
        paymentRecordMapper.insert(record);
    }
    /**
     * 根据sessionId查询支付记录
     */
    private PaymentRecord findPaymentRecordBySessionId(String sessionId) throws StripeException {
        QueryWrapper queryWrapper=QueryWrapper.create()
                .eq("stripeSessionId",sessionId);
        return  paymentRecordMapper.selectOneByQuery(queryWrapper);
    }
    /**
     * 查询最近的成功支付的记录
     */
    private PaymentRecord findLatestSuccessfulPayment(Long userId){
        QueryWrapper queryWrapper=QueryWrapper.create()
                .eq("userId", userId)
                .eq("status",PaymentStatusEnum.SUCCEEDED.getValue())
                .eq("productType",ProductTypeEnum.VIP_PERMANENT.getValue())
                .orderBy("createdTime",false)
                .limit(1);
        return paymentRecordMapper.selectOneByQuery(queryWrapper);
    }


    /**
     * 更新支付状态
     */
    private void updataPaymentStatus(Long recordId,PaymentStatusEnum status,String paymentSessionId) {
        PaymentRecord updataRecord=new PaymentRecord();
        updataRecord.setId(recordId);
        updataRecord.setStatus(status.getValue());
        updataRecord.setStripePaymentIntentId(paymentSessionId);
        paymentRecordMapper.update(updataRecord);
    }
    /**
     * 升级用户为VIP
     */
    private void upgradeUserToVip(Long userId){
        User user=new User();
        user.setId(userId);
        user.setVipTime(LocalDateTime.now());
        user.setUserRole(UserConstant.VIP_ROLE);
        userMapper.update(user);
    }
    /**
     * 创建Stripe退款
     */
    private Refund createStripeRefund(String stripePaymentIntentId) throws StripeException {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(stripePaymentIntentId)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)//退款原因 = 用户主动要求退款
                .build();
        return Refund.create(params);
    }
    /**
     * 更新退款记录
     */
    private void updataRefundRecord(Long recordId,String reason){
        PaymentRecord updataRecord=new PaymentRecord();
        updataRecord.setId(recordId);
        updataRecord.setStatus(PaymentStatusEnum.REFUNDED.getValue());
        updataRecord.setRefundTime(LocalDateTime.now());
        updataRecord.setRefundReason(reason);
        paymentRecordMapper.update(updataRecord);
    }
    /**
     * 撤销VIP身份
     */
    private void revokeVipStatus(Long userId){
        User upadataUser=new User();
        upadataUser.setId(userId);
        upadataUser.setVipTime(null);
        upadataUser.setUserRole(UserConstant.DEFAULT_ROLE);
        upadataUser.setQuota(UserConstant.DEFAULT_QUOTA);
        userMapper.update(upadataUser);
    }
}
