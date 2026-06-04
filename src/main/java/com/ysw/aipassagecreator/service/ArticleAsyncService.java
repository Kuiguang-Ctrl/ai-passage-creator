package com.ysw.aipassagecreator.service;

import com.google.gson.reflect.TypeToken;
import com.ysw.aipassagecreator.agent.ArticleAgentOrchestrator;
import com.ysw.aipassagecreator.agent.config.AgentConfig;
import com.ysw.aipassagecreator.manager.SseEmitterManager;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.entity.Article;
import com.ysw.aipassagecreator.model.enums.ArticlePhaseEnum;
import com.ysw.aipassagecreator.model.enums.ArticleStatusEnum;
import com.ysw.aipassagecreator.model.enums.SseMessageTypeEnum;
import com.ysw.aipassagecreator.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步服务
 */

//他是连接Contorller和AgentService的桥梁，负责再后台线程中启动文章生成流程，同时处理状态更新和SSE消息推送

@Service
@Slf4j
public class ArticleAsyncService {
    @Resource
    private ArticleService articleService;
    @Resource
    private SseEmitterManager sseEmitterManager;
    @Resource
    private ArticleAgentService articleAgentService;
    @Resource
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    @Resource
    private AgentConfig agentConfig;

    /**
     * 为了使用户交互方面更加完善，讲一整流程下来的异步文章流程分为三个阶段
     * 因为为了让用户在生成标题和生成大纲方面有多个选择和根据自己意愿修改的功能
     */


/*    *//**
     *异步执行文章生成
     * @param taskId   任务ID
     * @param topic   选题
     *//*
    public void executeArticleGeneration(String taskId,String topic){
        log.info("异步任务开启，task={},topic={}",taskId,topic);
        try {
            //更新状态为处理中
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING,null);
            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            //执行智能体编排，并通过SSE消息推送进度---整个文章生成流程的 “启动入口”
            //相当于编排的调用accpetd方法相当于调用hanlerAgenMessage
            articleAgentService.executerArticleGeneration(state,message->{
                //根据类型消息处理，并发送
                handlerAgentMessage(taskId,message,state);
            });
            //保存完整文章到数据库
            articleService.saveArticleContent(taskId,state);
            //更新状态已完成
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.COMPLETED,null);
            //推送完成消息
            sendSseMessage(taskId,SseMessageTypeEnum.ALL_COMPLETE,Map.of("taskId",taskId));
            //完成SSE连接--关闭当前SSE连接
            sseEmitterManager.complete(taskId);
            log.info("异步任务完成task={}",taskId);
        }catch (Exception e){
            log.error("异步任务失败，task={}",taskId,e);
            //更新状态为失败
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());
            //推送错误信息
            sendSseMessage(taskId,SseMessageTypeEnum.ERROR,Map.of("message",e.getMessage()));
            //完成SSE连接---关闭SSE连接
            sseEmitterManager.complete(taskId);
        }
    }*/


    //分为三个阶段
    /**
     * 阶段1：异步生成标题方案
     * @param taskId 任务ID
     * @param topic  选题
     * @param style  文章风格（可为空）
     */
    @Async("articleExecutor")
    public void  exectePhase1(String taskId,String topic,String style){
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段1异步任务开始, taskId={}, topic={}, style={}, 使用多智能体编排={}",
                taskId, topic, style, useOrchestrator);
        try{
            //更新状态和阶段
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.PROCESSING,null);
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);
            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);
            //执行阶段1：生成标题方案
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase1_GenerataeTitles(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase1_GenerateTitles(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            }
            //保存标题方案到数据库
            articleService.saveTitleOptions(taskId,state.getTitleOptions());

            //更新阶段为等待选择标题
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);

            //推送标题方案生成完成消息
            Map<String,Object> data = new HashMap<>();
            data.put("titleOptions",state.getTitleOptions());
            sendSseMessage(taskId,SseMessageTypeEnum.TITLES_GENERATED,data);

            log.info("阶段1异步任务完成taskId={}",taskId);
        }catch (Exception e){
            log.error("阶段1异步任务失败，taskId={}",taskId,e);
            //更新状态为失败
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());
            //完成SSE连接,关闭连接
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段2：异步生成大纲（用户确认标题后调用）
     * 从数据库读取用户确认的标题和补充描述userDescription,让后调用智能体生成大纲
     * @param taskId
     */
    @Async("articleExecutor")
    public void exectePhase2(String taskId){
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段2异步任务开始, taskId={}, 使用多智能体编排={}", taskId, useOrchestrator);
        try{
            //获取文章信息

            Article article = articleService.queryChain()
                    .eq(Article::getTaskId, taskId)
                    .one();
            if(article==null){
                throw new RuntimeException("文章不存在");
            }
            //创建文件状态
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setUserDescription(article.getUserDescription());

            //设置标题
            ArticleState.TitleResult title=new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            //执行阶段2：生成大纲
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase2_GenerataeOutTitles(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase2_GenerateOutline(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            }

            //保存大纲到数据库
            Article articleToUpdata = articleService.queryChain()
                    .eq(Article::getTaskId, taskId)
                    .one();
            articleToUpdata.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
            articleService.updateById(articleToUpdata);

            //更新阶段为等待编辑大纲
            articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);

            //推送大纲生成完成消息
            Map<String,Object> data = new HashMap<>();
            data.put("outline",state.getOutline().getSections());
            sendSseMessage(taskId,SseMessageTypeEnum.OUTLINE_GENERATED,data);

            log.info("阶段2：异步任务已完成taskId={}",taskId);
        }catch (Exception e){
            log.error("阶段2：异步任务失败taskId={}",taskId,e);
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());
            sendSseMessage(taskId,SseMessageTypeEnum.ERROR,Map.of("message",e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 阶段3：异步生成正文+配图（用户确认大纲后调用）
     * @param taskId
     */
    @Async
    public void exectePhase3(String taskId){
        boolean useOrchestrator = agentConfig.isOrchestratorEnabled();
        log.info("阶段3异步任务开始, taskId={}, 使用多智能体编排={}", taskId, useOrchestrator);
        try{
            Article article = articleService.queryChain()
                    .eq(Article::getTaskId, taskId)
                    .one();
            if(article==null){
                throw new RuntimeException("文章不存在");
            }

            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());

            //从数据库中获取允许的配图方式
            List<String> enabledMethods=null;
            if(article.getEnabledImageMethods()!=null){
                enabledMethods=GsonUtils.fromJson(
                        article.getEnabledImageMethods(),
                        new TypeToken<List<String>>(){});
            }
            state.setEnabledImageMethods(enabledMethods);

            //设置标题
            ArticleState.TitleResult title=new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            //设置大纲
            List<ArticleState.OutlineSection> outlineSections=GsonUtils.fromJson(article.getOutline(),
                    new TypeToken<List<ArticleState.OutlineSection>>(){});
            ArticleState.OutlineResult outlineResult=new ArticleState.OutlineResult();
            outlineResult.setSections(outlineSections);
            state.setOutline(outlineResult);

            //执行任务3：生成正文加配图
            //Consumer<T> 是 JDK 函数式接口可以直接用lambda实现accept，从而实现handlerAgentMessage进行消息推送
            if (useOrchestrator) {
                articleAgentOrchestrator.executePhase3_GenerataeContent(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            } else {
                articleAgentService.executePhase3_GenerateContent(state, message -> {
                    handlerAgentMessage(taskId, message, state);
                });
            }

            //保存完整文章到数据库
            articleService.saveArticleContent(taskId,state);

            //更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED,null);

            //推送完成消息
            sendSseMessage(taskId,SseMessageTypeEnum.ALL_COMPLETE,Map.of("taskId",taskId));

            //完成SSE连接
            sseEmitterManager.complete(taskId);
            log.info("阶段3异步任务完成，taskId={}",taskId);
        }catch (Exception e){
            log.error("阶段3异步任务失败taskId={}",taskId,e);

            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());
            sendSseMessage(taskId,SseMessageTypeEnum.ERROR,Map.of("message",e.getMessage()));
            sseEmitterManager.complete(taskId);
        }

    }

    /**
     *处理智能体消息并推送
     * @param taskId
     * @param message
     * @param state
     */
    private void handlerAgentMessage(String taskId,String message,ArticleState state){

        Map<String,Object> data=buildMessageData(message,state);
        if(data!=null){
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
        }
    }

//把智能体生成的消息 → 整理成前端能看懂的格式 → 推送给前端显示！
    /**
     * 构建消息数据
     * @param message
     * @param state
     * @return
     */
    private Map<String,Object> buildMessageData(String message,ArticleState state){
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();
        //startsWith判断前缀---------字符串.startsWith(前缀)
        if(message.startsWith(streamingPrefix2)){
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING,message.substring(streamingPrefix2.length()));
        }
        if(message.startsWith(streamingPrefix3)){
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING,message.substring(streamingPrefix3.length()));
        }
        if(message.startsWith(imageCompletePrefix)){
            String imageJson=message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }
        //处理完成消息（枚举值）
        return buildCompleteMessageData(message,state);
    }

    /**
     * 构建流式输出数据
     * @param type
     * @param content
     * @return
     */
    private Map<String,Object>  buildStreamingData(SseMessageTypeEnum type,String content){
        Map<String,Object> data = new HashMap<>();
        data.put("type",type.getValue());
        data.put("content",content);
        return data;
    }
    /**
     * 构建图片完成数据
     * @param imageJson
     * @return
     */
    private Map<String,Object> buildImageCompleteData(String imageJson){
        Map<String,Object> data = new HashMap<>();
        data.put("type",SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image",GsonUtils.fromJson(imageJson,ArticleState.ImageResult.class));
        return data;
    }
    /**
     * 构建完成消息数据
     * @param message
     * @param state
     * @return
     */
    private Map<String,Object> buildCompleteMessageData(String message,ArticleState state){
        Map<String, Object> data = new HashMap<>();

        // 标题生成完成（智能体1）
        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        }
        // 大纲生成完成（智能体2）
        else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        }
        // 正文生成完成（智能体3）
        else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        }
        // 配图需求分析完成（智能体4）
        else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        }
        // 配图生成完成（智能体5）
        else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        }
        // 图文合成完成
        else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        }else{
            return null;
        }
        return data;
    }
    /**
     * 发送SSE消息
     */
    private void sendSseMessage(String taskId,SseMessageTypeEnum type,Map<String,Object> additionalData){
        Map<String,Object> data = new HashMap<>();
        data.put("type",type.getValue());
        data.putAll(additionalData);
        sseEmitterManager.send(taskId, GsonUtils.toJson(data));
    }


}
//前端发起文章生成请求后，Controller 调用 ArticleAsyncService 的 executeArticleGeneration 异步方法，
// 先更新文章状态为处理中，并创建 ArticleState 状态对象存储文章所有内容；
// 接着调用 articleAgentService.executerArticleGeneration 启动智能体编排，同时传入 Lambda 回调函数。
// 智能体依次生成标题、大纲、正文、配图等内容，每一步都会发送消息并调用 streamHandler.accept 触发回调，
// 自动进入 handlerAgentMessage 消息处理方法；该方法调用 buildMessageData 分拣消息类型，
// 通过前缀判断是大纲流式、正文流式、图片完成还是阶段完成消息，
// 再分别调用 buildStreamingData、buildImageCompleteData、buildCompleteMessageData 打包数据，
// 最终通过 sseEmitterManager.send 根据 taskId 将格式化后的 JSON 数据实时推送到前端页面展示。