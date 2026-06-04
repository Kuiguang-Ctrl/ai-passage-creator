package com.ysw.aipassagecreator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync//开启异步底层多线程
public class Asyncconfig {
    /**
     * 文章生成--异步线程池
     */
    @Bean(name = "articleExecutor")
    public Executor articleExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程数
        executor.setCorePoolSize(5);
        //最大线程数
        executor.setMaxPoolSize(10);
        //队列容量
        executor.setQueueCapacity(100);
        //线程名称前缀
        executor.setThreadNamePrefix("article-async");
        //拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //等待所有线程完成后再关闭线程池
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

}
