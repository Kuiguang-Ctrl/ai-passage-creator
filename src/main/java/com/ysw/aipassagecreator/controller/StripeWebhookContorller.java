package com.ysw.aipassagecreator.controller;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.ysw.aipassagecreator.service.PaymentService;
import com.ysw.aipassagecreator.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.model.Event;
import java.util.Map;

/**
 * Stripe webhook 控制器
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
@Hidden
public class StripeWebhookContorller {
    @Resource
    private PaymentService paymentService;

    /**
     * 处理 Stripe Webhook 回调
     */
    @PostMapping("/stripe")
    public String handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            Event event = paymentService.constructEvent(payload, sigHeader);
            log.info("收到Stripe事件: {}", event.getType());

            switch (event.getType()) {
                case "checkout.session.completed":
                case "checkout.session.async_payment_succeeded":
                    // ✅ 这行是关键修复
                    Session session = (Session) event.getData().getObject();
                    paymentService.handlePaymentSuccess(session);
                    break;

                default:
                    log.info("未处理事件: {}", event.getType());
            }

            return "success";
        } catch (Exception e) {
            log.error("Webhook处理失败", e);
            return "error";
        }
    }
}
