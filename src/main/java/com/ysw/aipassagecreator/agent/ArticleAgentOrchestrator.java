package com.ysw.aipassagecreator.agent;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.ysw.aipassagecreator.agent.agents.*;
import com.ysw.aipassagecreator.agent.config.AgentConfig;
import com.ysw.aipassagecreator.agent.context.StreamHandlerContext;
import com.ysw.aipassagecreator.agent.parallel.ParallelImageGenerator;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.enums.SseMessageTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 文章智能体编排器
 * 使用 Spring AI Alibaba 的 StateGraph 编排多个 Agent
 *
 * @author AI Passage Creator
 */
@Service
@Slf4j
public class ArticleAgentOrchestrator {

    @Resource
    private AgentConfig agentConfig;

    @Resource
    private TitleGeneratorAgent titleGeneratorAgent;

    @Resource
    private OutlineGeneratorAgent outlineGeneratorAgent;

    @Resource
    private ContentGeneratorAgent contentGeneratorAgent;

    @Resource
    private ImageAnalyzerAgent imageAnalyzerAgent;

    @Resource
    private ParallelImageGenerator parallelImageGenerator;

    @Resource
    private ContentMergerAgent contentMergerAgent;

    // region 状态键常量

    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_STYLE = "style";
    private static final String KEY_USER_DESCRIPTION = "userDescription";
    private static final String KEY_MAIN_TITLE = "mainTitle";
    private static final String KEY_SUB_TITLE = "subTitle";
    private static final String KEY_TITLE_OPTIONS = "titleOptions";
    private static final String KEY_OUTLINE = "outline";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_CONTENT_WITH_PLACEHOLDERS = "contentWithPlaceholders";
    private static final String KEY_IMAGE_REQUIREMENTS = "imageRequirements";
    private static final String KEY_IMAGES = "images";
    private static final String KEY_FULL_CONTENT = "fullContent";
    private static final String KEY_ENABLED_IMAGE_METHODS = "enabledImageMethods";

    /**
     * 阶段1：生成标题方案
     *
     * @param state
     * @param streamHandler
     */
    public void executePhase1_GenerataeTitles(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段1（多智能体编排）：开始生成标题方案, taskId={}", state.getTaskId());

        try {
            //构建输入状态
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_TOPIC, state.getTopic());
            inputs.put(KEY_STYLE, state.getStyle());

            //构建并执行图
            StateGraph graph =buildPhase1Graph();
            CompiledGraph compiledGraph=graph.compile();//校验【节点 + 箭头组成的流程图】

            Optional<OverAllState> result=compiledGraph.invoke(inputs);//invoke ()：同步启动整张状态图流转

            if(result.isPresent()){//true：流程图正常执行完毕，存在最终的 OverAllState 对象，可以 result.get() 取出状态
                OverAllState finalState=result.get();

                @SuppressWarnings("unchecked")
                List<ArticleState.TitleOption> titleOptions=finalState.value(KEY_TITLE_OPTIONS)
                        .map(v->(List<ArticleState.TitleOption>)v)
                        .orElse(null);

                if(titleOptions!=null){
                    state.setTitleOptions(titleOptions);
                    streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
                    log.info("阶段1（多智能体编排）：标题方案生成完成，数量={}",titleOptions.size());
                }else {
                    throw new RuntimeException("标题方案生成失败：结果为空");
                }
            }else {
                throw new RuntimeException("标题方案生成失败：执行结果为空");
            }
        }catch (Exception e){
            log.error("阶段1（多智能体编排）：标题方案生成失败，taskId={}",state.getTaskId(),e);
            throw new RuntimeException("标题方案生成失败"+e.getMessage(),e);
        }
    }

    /**
     * 阶段2：生成大纲
     */
    public void executePhase2_GenerataeOutTitles(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段2（多智能体编排）：开始生成大纲, taskId={}", state.getTaskId());

        //设置流式处理器到ThreadLocal
        StreamHandlerContext.set(streamHandler);
        try{
            // 构建输入状态
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(KEY_SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(KEY_USER_DESCRIPTION, state.getUserDescription());
            inputs.put(KEY_STYLE, state.getStyle());

            StateGraph graph=buildPhase2Graph();
            CompiledGraph compiledGraph = graph.compile();

            Optional<OverAllState> result=compiledGraph.invoke(inputs);
            if(result.isPresent()){
                OverAllState finalState=result.get();//可以 result.get() 取出状态

                ArticleState.OutlineResult outline=finalState.value(KEY_OUTLINE)
                        .map(v->{
                            if(v instanceof ArticleState.OutlineResult){
                                return (ArticleState.OutlineResult)v;
                            }
                            return null;
                        }).orElse(null);

                if(outline!=null){
                    state.setOutline(outline);
                    streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
                    log.info("阶段2（多智能体编排）：大纲生成，章节数={}",outline.getSections().size());
                }else {
                    throw new RuntimeException("大纲生成失败:结果为空");
                }
            }else {
                throw new RuntimeException("大纲生成失败：执行结果为空");
            }
        }catch (Exception e){
            log.error("阶段2（多智能体编排）：大纲生成失败taskId={}",state.getTaskId(),e);
            throw new RuntimeException("大纲生成失败"+e.getMessage(),e);
        }finally {
            //清理ThreadLocal
            StreamHandlerContext.clear();
        }
    }

    /**
     * 生成正文加配图
     */
    public void executePhase3_GenerataeContent(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段3（多智能体编排）：开始生成正文加配图, taskId={}", state.getTaskId());

        //设置流式处理器到ThreadLocal
        StreamHandlerContext.set(streamHandler);
        try{

            // 构建输入状态（不再包含 streamHandler，避免序列化问题）
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(KEY_SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(KEY_OUTLINE, state.getOutline());
            inputs.put(KEY_STYLE, state.getStyle());
            inputs.put(KEY_ENABLED_IMAGE_METHODS, state.getEnabledImageMethods());

            //够建并执行图
            StateGraph graph=buildPhase3Graph();
            CompiledGraph compiledGraph=graph.compile();

            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if(result.isPresent()){
                OverAllState finalState = result.get();//可以 result.get() 取出状态

                //提取带占位符的正文
                String contentWithPlaceholders = finalState.value(KEY_CONTENT_WITH_PLACEHOLDERS)
                        .map(Object::toString)
                        .orElse(null);
                String content=finalState.value(KEY_CONTENT)
                        .map(Object::toString)
                        .orElse(null);


                @SuppressWarnings("unchecked")
                List<ArticleState.ImageRequirement> imageRequirements=finalState.value(KEY_IMAGE_REQUIREMENTS)
                        .map(v->(List<ArticleState.ImageRequirement>)v)
                        .orElse(null);

                @SuppressWarnings("unchecked")
                List<ArticleState.ImageResult> images=finalState.value(KEY_IMAGES)
                        .map(v->(List<ArticleState.ImageResult>)v)
                        .orElse(null);

                String fullContent=finalState.value(KEY_FULL_CONTENT)
                        .map(Object::toString)
                        .orElse(null);

                //更新状态
                if(contentWithPlaceholders!=null){
                    state.setContent(contentWithPlaceholders);
                }else if(content!=null){
                    state.setContent(content);
                }
                streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

                if(imageRequirements!=null){
                    state.setImageRequirements(imageRequirements);
                    streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
                }

                if(images!=null){
                    state.setImages(images);
                    streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
                }

                if(fullContent!=null){
                    state.setFullContent(fullContent);
                    streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
                }

                log.info("阶段3（多智能体编排）：正文+配图生成完成, 正文长度={}, 图片数={}",
                        contentWithPlaceholders != null ? contentWithPlaceholders.length() : (content != null ? content.length() : 0),
                        images != null ? images.size() : 0);

            } else {
                throw new RuntimeException("正文+配图生成失败：执行结果为空");
            }

        } catch (Exception e) {
            log.error("阶段3（多智能体编排）：正文+配图生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文+配图生成失败: " + e.getMessage(), e);
        } finally {
            // 清理 ThreadLocal
            StreamHandlerContext.clear();
        }
    }

    /**
     * 构建阶段1图：标题生成
     * @return
     * @throws GraphStateException
     */
    private StateGraph buildPhase1Graph() throws GraphStateException {
        //1. 创建键策略工厂
        KeyStrategyFactory keyStrategyFactory=createKeyStrategyFactory();

        //2. 构建并返回状态图
        return new StateGraph(keyStrategyFactory)//实例化一张空白流程图，绑定：全局状态规则（从工厂拿到 OverAllState 映射规则）
                .addNode("title_generator", node_async(titleGeneratorAgent))
                //addNode(节点名称, 节点执行处理器)，node_async包装成异步执行节点，Agent 异步运行

                .addEdge(START, "title_generator")//LangGraph 内置流程图起始常量（入口标记）
                .addEdge("title_generator", END);//title_generator执行完毕 → 直接结束整张图
    }
    /**
     * 构建阶段2图：大纲生成
     */
    private StateGraph buildPhase2Graph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory();

        return new StateGraph(keyStrategyFactory)
                .addNode("outline_generator", node_async(outlineGeneratorAgent))
                .addEdge(START, "outline_generator")
                .addEdge("outline_generator", END);
    }

    /**
     * 构建阶段3图：正文+配图生成（顺序执行）
     * 流程：正文生成 -> 配图需求分析 -> 并行配图生成 -> 图文合成
     */
    private StateGraph buildPhase3Graph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = createKeyStrategyFactory();

        return new StateGraph(keyStrategyFactory)
                // 节点定义
                .addNode("content_generator", node_async(contentGeneratorAgent))
                .addNode("image_analyzer", node_async(imageAnalyzerAgent))
                .addNode("parallel_image_generator", node_async(parallelImageGenerator))
                .addNode("content_merger", node_async(contentMergerAgent))
                // 边定义：顺序执行
                .addEdge(START, "content_generator")
                .addEdge("content_generator", "image_analyzer")
                .addEdge("image_analyzer", "parallel_image_generator")
                .addEdge("parallel_image_generator", "content_merger")
                .addEdge("content_merger", END);
    }



    /**
     * 创建状态键策略工厂
     * 所有键都使用替换策略
     */
    private KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(KEY_TASK_ID, new ReplaceStrategy());
            strategies.put(KEY_TOPIC, new ReplaceStrategy());
            strategies.put(KEY_STYLE, new ReplaceStrategy());
            strategies.put(KEY_USER_DESCRIPTION, new ReplaceStrategy());
            strategies.put(KEY_MAIN_TITLE, new ReplaceStrategy());
            strategies.put(KEY_SUB_TITLE, new ReplaceStrategy());
            strategies.put(KEY_TITLE_OPTIONS, new ReplaceStrategy());
            strategies.put(KEY_OUTLINE, new ReplaceStrategy());
            strategies.put(KEY_CONTENT, new ReplaceStrategy());
            strategies.put(KEY_CONTENT_WITH_PLACEHOLDERS, new ReplaceStrategy());
            strategies.put(KEY_IMAGE_REQUIREMENTS, new ReplaceStrategy());
            strategies.put(KEY_IMAGES, new ReplaceStrategy());
            strategies.put(KEY_FULL_CONTENT, new ReplaceStrategy());
            strategies.put(KEY_ENABLED_IMAGE_METHODS, new ReplaceStrategy());
            return strategies;
        };
    }
}