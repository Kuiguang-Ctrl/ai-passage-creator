package com.ysw.aipassagecreator.agent.context;

import java.util.function.Consumer;

/**
 * 流式处理器上下文
 * 使用ThreadLocal保存streamHander,避免将其放入StateGraph中（无法序列化）
 */
//每个线程创建一个Consumer，防止串了
public class StreamHandlerContext {

    private static final ThreadLocal<Consumer<String>> STREAM_HANDLER = new ThreadLocal<>();

    /**
     * 设置流式输出处理器
     */

    public static void set(Consumer<String> handler){
        STREAM_HANDLER.set(handler);
    }

    /**
     * 获取流输出处理器
     */
    public static Consumer<String> get(){
        return STREAM_HANDLER.get();
    }
    /**
     * 清理上下文
     * 务必使用完毕后调用，避免内存泄漏(站在内存不释放)
     */
    public static  void clear(){
        STREAM_HANDLER.remove();
    }

    /**
     * 发送消息到流式输出
     * 如果handler不存在则忽略
     */
    /*就会立刻把消息交给当前线程的 Consumer 处理
    也就是执行流式输出！*/
    public static void send(String message){
        Consumer<String> handler = STREAM_HANDLER.get();
        if(handler != null&&message!=null){
            handler.accept(message);
        }
    }
}
