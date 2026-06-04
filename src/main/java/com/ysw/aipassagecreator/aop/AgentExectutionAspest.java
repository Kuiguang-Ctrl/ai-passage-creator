package com.ysw.aipassagecreator.aop;

import com.ysw.aipassagecreator.annotation.AgentExecution;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.entity.AgentLog;
import com.ysw.aipassagecreator.service.AgentLogService;
import com.ysw.aipassagecreator.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能体执行AOP切面
 * 自动记录智能体执行日志和性能数据
 */
@Aspect
@Component
@Slf4j
public class AgentExectutionAspest {
    @Resource
    private AgentLogService agentLogService;

    /**
     *
     * @param pjp  环绕通知的必备
     * @param agentExecution
     * @return
     * @throws Throwable
     */
    @Around("@annotation(agentExecution)")//agentExecution要拦截的注解，参数里面的agentExecution是注解的示例对象
    public Object aroundAgentExectution(ProceedingJoinPoint pjp, AgentExecution agentExecution) throws Throwable {
        long startTime = System.currentTimeMillis();
        LocalDateTime startDataTime= LocalDateTime.now();

        //提取taskId和输入数据
        String taskId=extractTaskId(pjp);
        String inputData=extractInputData(pjp);//提取输入数据（参数数据）
        String prompt=exetratPrompt(pjp);//提取prompt（方法名推断）
        //创建日志对象
        AgentLog agentLog=AgentLog.builder()
                .taskId(taskId)
                .agentName(agentExecution.value())
                .startTime(startDataTime)
                .inputData(inputData)
                .prompt(prompt)
                .build();

        Object result=null;
        try {
            //执行目标方法
            result=pjp.proceed();//调用原始业务方法，有返回值就给result,不写proceed不能执行，返回值必须是Object

            //记录成功状态
            agentLog.setStatus("SUCCESS");
            agentLog.setEndTime(LocalDateTime.now());
            //不能用LocalDateTime.now（）替代System.currentTimeMillis,因为LocalDateTime不能运算
            agentLog.setDurationMs ((int)(System.currentTimeMillis()-startTime));
            agentLog.setOutputData(extractOutputData(result));

            log.info("智能体执行成功；：{}，taskId={},耗时={}ms",
                    agentExecution.value(),taskId,agentLog.getDurationMs());
        }catch (Throwable e){
            //记录失败状态
            agentLog.setStatus("FAILED");
            agentLog.setEndTime(LocalDateTime.now());
            agentLog.setDurationMs ((int)(System.currentTimeMillis()-startTime));
            agentLog.setErrorMessage(e.getMessage()!=null?e.getMessage():
                    e.getMessage().getClass().getName());
            log.error("智能体执行失败：{}，taskId,错误={}",agentExecution.value(),taskId,e.getMessage(),e);

            throw e;
        }finally {
            //异步保存日志
            agentLogService.saveLogAsync(agentLog);
        }
        return result;
    }
    //从方法参数中提取taskId
    private String extractTaskId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();//获取被拦截方法的参数
        if(args == null || args.length==0){
            return "nukown";
        }
        //优先充ArticleState中获取
        //instanceof就是用来判断：一个对象 是不是 某个类 / 接口的实例
        for(Object arg:args){
            if(arg instanceof ArticleState){
                return ((ArticleState)arg).getTaskId();
            }
        }
        //尝试从第一个String参数中获取（可能是taskId）
        for(Object arg:args){
            if(arg instanceof String){
                return (String)arg;
            }
        }
        return "nukown";
    }
    /**
     * 提取输入数据
     */
    private String extractInputData(ProceedingJoinPoint pjp) {
        try {
            Object[] args = pjp.getArgs();
            if(args == null || args.length==0){
                return null;
            }
            Map<String,Object> inputMap=new HashMap<>();
            MethodSignature signature = (MethodSignature) pjp.getSignature();//那到被拦截方法的详细信息的标准写法
            String[] parameterNames = signature.getParameterNames();// 获取参数名数组（顺序和参数值完全一致)
            for(int i=0;i<args.length&&i<parameterNames.length;i++){
                Object arg = args[i];
                //只记录基本类型的信息，避免数据过大
                if(arg instanceof String ||arg instanceof Number||arg instanceof Boolean){
                    inputMap.put(parameterNames[i],arg);
                }else if(arg instanceof ArticleState){
                    ArticleState state=(ArticleState)arg;
                    inputMap.put("taskId",state.getTaskId());
                    if (state.getTitle()!=null) {
                        inputMap.put("MainTitle",state.getTitle().getMainTitle());
                    }
                }
            }
            return inputMap.isEmpty()?null: GsonUtils.toJson(inputMap);
        }catch (Exception e){
            log.warn("提取输入数据失败",e);
            return null;
        }
    }
    /**
     * 提取使用的 Prompt（尝试从方法参数或 ArticleState 获取）
     */
    private String exetratPrompt(ProceedingJoinPoint pjp) {
        try {
            //可以通过方法名推断使用的prompt
            //或从参数中提取，这里简化处理
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            // 2. 获取【Java 反射的 Method 对象】（代表这个方法本身）
            Method method = signature.getMethod();
            // 3. 拼接：类名（简写） + . + 方法名
            return method.getDeclaringClass().getSimpleName()+"."+method.getName();
        }catch (Exception e){
            return   null;
        }
    }
    /**
     * 提取输出数据(简化版）
     */
    private String extractOutputData(Object result){
        try {
            if (result==null){
                return null;
            }
            //只记录简单类型，避免数据过大
            if(result instanceof String||result instanceof Number||result instanceof Boolean){
                return String.valueOf(result);
            }
            if(result instanceof java.util.List){
                return "{\"listSize\":"+((java.util.List)result).size();
            }
            return "{\"type\":"+result.getClass().getSimpleName()+"\"}";
        }catch (Exception e){
            log.warn("提取数据失败",e);
            return null;
        }
    }
}
