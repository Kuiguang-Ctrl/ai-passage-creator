package com.ysw.aipassagecreator.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ysw.aipassagecreator.mapper.AgentLogMapper;
import com.ysw.aipassagecreator.model.entity.AgentLog;
import com.ysw.aipassagecreator.model.vo.AgentExecutionStats;
import com.ysw.aipassagecreator.service.AgentLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AgentLogServiceImpl extends ServiceImpl<AgentLogMapper,AgentLog> implements AgentLogService {
    /**
     * 异步保存日志
     * @param agentLog  日志对象
     */
    @Override
    @Async
    public void saveLogAsync(AgentLog agentLog) {
        try{
            this.save(agentLog);
            log.info("智能体日志已保存，taskId={},agentName={},status={},durationMs={}",
                    agentLog.getTaskId(), agentLog.getAgentName(), agentLog.getStatus(),
                    agentLog.getDurationMs());
        }catch (Exception e){
            log.error("保存智能体日志失败，taskId={},agentName={}",
                    agentLog.getTaskId(), agentLog.getAgentName(), e);
        }
    }
    /**
     * 根据任务id获取所有日志
     * @param taskId 任务id
     * @return 日志列表
     */
    @Override
    public List<AgentLog> getLogByTaskId(String taskId) {
        QueryWrapper queryWrapper=QueryWrapper.create()
                .eq("taskId",taskId)
                .orderBy("createTime",true);
        return this.list(queryWrapper);
    }
    /**
     * 获取任务执行统计信息
     * @param taskId  任务id
     * @return   执行统计
     */
    @Override
    public AgentExecutionStats getExecutionStats(String taskId) {
        List<AgentLog> logs = getLogByTaskId(taskId);
        if(logs==null||logs.isEmpty()){
            return AgentExecutionStats.builder()
                    .taskId(taskId)
                    .agentCount(0)
                    .totalDurationMs(0)
                    .overallStatus("NOT_FOUND")
                    .build();
        }
        //计算统计数据
        int totalDuration=0;
        Map<String,Integer> agentDurations=new HashMap<>();
        String overallStatus="SUCCESS";
        for(AgentLog log:logs){
            //累加总耗时
            if(log.getDurationMs()!=0){
                totalDuration+=log.getDurationMs();
                agentDurations.put(log.getAgentName(),log.getDurationMs());
            }
            //判断总体状态--只要有一个失败就是失败（FAILED），只有不是失败才能判断是否是RUNNING（运行中）
            if("FAILED".equals(log.getStatus())){
                overallStatus="FAILED";
            }else if("RUNNING".equals(log.getStatus())&&!"FAILED".equals(overallStatus)){
                overallStatus="RUNNING";
            }
        }
        return AgentExecutionStats.builder()
                .taskId(taskId)
                .totalDurationMs(totalDuration)
                .agentCount(logs.size())
                .agentDurations(agentDurations)
                .overallStatus(overallStatus)
                .logs(logs)
                .build();
    }
}
