package com.ysw.aipassagecreator.service;

import com.mybatisflex.core.service.IService;
import com.ysw.aipassagecreator.annotation.AgentExecution;
import com.ysw.aipassagecreator.model.entity.AgentLog;
import com.ysw.aipassagecreator.model.vo.AgentExecutionStats;

import java.util.List;

/**
 * 智能体日志服务
 */
//iservice---MyBatis-plus的，继承了可以拥有自动的增删改查等基本操作
public interface AgentLogService extends IService<AgentLog> {
    /**
     * 异步保存日志
     * @param agentLog  日志对象
     */
    void saveLogAsync(AgentLog agentLog);

    /**
     * 根据任务id获取所有日志
     * @param taskId 任务id
     * @return 日志列表
     */
    List<AgentLog> getLogByTaskId(String taskId);

    /**
     * 获取任务执行统计信息
     * @param taskId  任务id
     * @return   执行统计
     */
    AgentExecutionStats getExecutionStats(String taskId);
}
