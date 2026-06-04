package com.ysw.aipassagecreator.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.ysw.aipassagecreator.constant.ArticleConstant.*;

import static com.ysw.aipassagecreator.constant.ArticleConstant.*;

/**
 *  SSE Emitter管理器
 *
 */
@Component
@Slf4j
public class SseEmitterManager {
    /**
     * 存储所有的SseEmitter
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    public SseEmitter createSseEmitter(String taskId) {
        /**
         * 这些回调是框架自动判断的，确保异常时可以自动清理
         */
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        //设置超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时，taskId{}",taskId);
            emitterMap.remove(taskId);
        });
        //设置完成回调
        emitter.onCompletion(()->{
            log.info("SSE连接完成,taskId{}",taskId);
            emitterMap.remove(taskId);
        });
        //设置错误回调
        emitter.onError((e)->{
            log.error("SSE连接错误，,taskId{}",taskId);
            emitterMap.remove(taskId);
        });

        emitterMap.put(taskId, emitter);
        log.info("SSE 连接以创建,taskId{}",taskId);
        return emitter;
    }

    /**
     * 发送消息
     * @param taskId  任务id
     * @param messages  任务内容
     */
    public void send(String taskId, String messages) {
        log.info("尝试发送消息，taskId={}, emitterMap大小={}, 包含key={}",
                taskId, emitterMap.size(), emitterMap.containsKey(taskId));
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.info("SSE Emitter 不存在 taskId{}",taskId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .data(messages)
                    .reconnectTime(SSE_RECONNECT_TIME_MS));
            log.debug("消息发送成功，taskId{},messages",taskId,messages);
        }catch (Exception e){
            log.error("SSE消息发送失败，taskId{}",taskId);
            emitterMap.remove(taskId);
        }
    }

    /**
     * 完成连接;任务完成
     * @param taskId
     */
    public void complete(String taskId) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.info("SSE不存在，taskId{}",taskId);
            return;
        }

        try {
            emitter.complete();
            log.info("连接已完成taskId{}",taskId);
        }catch (Exception e){
            log.error("连接失败taskId{}",taskId);
        }finally {
            emitterMap.remove(taskId);
        }
    }
    /**
     * 检查Emitter是否存在
     * @param taskId
     * @return
     */
    public boolean exists(String taskId) {
        return emitterMap.containsKey(taskId);
    }
}
