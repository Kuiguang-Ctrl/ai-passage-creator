package com.ysw.aipassagecreator.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;

import com.google.common.reflect.TypeToken;
import com.ysw.aipassagecreator.annotation.AgentExecution;
import com.ysw.aipassagecreator.constant.PromptConstant;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.dto.image.ImageRequest;
import com.ysw.aipassagecreator.model.enums.ArticleStyleEnum;
import com.ysw.aipassagecreator.model.enums.ImageMethodEnum;
import com.ysw.aipassagecreator.model.enums.SseMessageTypeEnum;
import com.ysw.aipassagecreator.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class ArticleAgentService {
    private final DashScopeChatModel chatModel;
    private final CosService cosService;
    private final ImageServiceStrategy imageServiceStrategy;

    public ArticleAgentService(DashScopeChatModel chatModel,
                               CosService cosService,
                               ImageServiceStrategy imageServiceStrategy) {
        this.chatModel = chatModel;
        this.cosService = cosService;
        this.imageServiceStrategy = imageServiceStrategy;
    }
    /**
     * 为了使用户交互方面更加完善，讲一整流程下来的异步文章流程分为三个阶段
     * 因为为了让用户在生成标题和生成大纲方面有多个选择和根据自己意愿修改的功能
     */



   /**
     * 整体流程
     *
     * @param state         文章状态
     * @param streamHandler 流式输出回调函数
     *//*
    public void executerArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        try {
            //智能体1：生成标题
            log.info("智能体1：开始生成标题，taskId:{}", state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            //智能体2：生成大纲
            log.info("智能体2：开始生成大纲，taskId:{}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());


            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            //图文合成：将配图插入正文
            log.info("开始图文生成，taskId:{}", state.getTaskId());
            mergeImageIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            log.info("文章生成完成，taskId:{}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败，taskId:{}", state.getTaskId());
            throw new RuntimeException("文章生成失败：" + e.getMessage(), e);
        }
    }*/

    /**
     * 阶段1：生成标题方案（3-5个）
     * @param state          文章状态
     * @param streamHandler  流式输出处理器
     */
    public void executePhase1_GenerateTitles(ArticleState state,Consumer<String> streamHandler){
        try{
            //智能体1；生成标题方案
            log.info("阶段1：开始生成标题方案，taskId={}",state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());//把当前任务 ID，丢给 “结果输出器”，让它把结果推给前端
            log.info("阶段1：标题方案生成完成，taskId={},optionsCount={}",state.getTaskId(),state.getTitleOptions().size());
        }catch (Exception e){
            log.error("阶段1：生成标题失败，taskId={}",state.getTaskId(),e);
            throw new RuntimeException("标题方案生成失败："+e.getMessage(),e);
        }
    }

    /**
     * 阶段2：生成大纲
     * @param state           文章状态
     * @param streamHandler   流式输出处理器
     */
    public void executePhase2_GenerateOutline(ArticleState state,Consumer<String> streamHandler){
        try{
            //智能体2生成大纲（流式输出）
            log.info("阶段2：开始生成大纲taskId={}",state.getTaskId());
            agent2GenerateOutline(state,streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            log.info("阶段2：大纲生成完毕taskId={}",state.getTaskId());
        }catch (Exception e){
            log.error("阶段2：大纲生成失败taskId={}",state.getTaskId(),e);
            throw new RuntimeException("大纲生成失败"+e.getMessage(),e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     * @param state
     * @param streamHandler
     */
    public void executePhase3_GenerateContent(ArticleState state,Consumer<String> streamHandler){
        try{
            //智能体三：生成正文（流式输出）
            log.info("阶段3：生成正文taskId={}",state.getTaskId());
            agent3GenerateContent(state,streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            //智能体4：分析配图分析
            log.info("阶段3：开始分析配图需求，taskId={}",state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            //智能体5：生成配图
            log.info("阶段3：开始生成配图taskId={}",state.getTaskId());
            agent5GenerateImages(state,streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            //图文合成
            log.info("阶段3：开始图文合成taskId={}",state.getTaskId());
            mergeImageIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("阶段3：正文生成完成taskId={}",state.getTaskId());
        }catch (Exception e){
            log.error("阶段3：正文生成失败taskId={}",state.getTaskId(),e);
            throw new RuntimeException("正文生成失败"+e.getMessage(),e);
        }
    }

    /**
     * 智能体1：生成标题方案（3-5个）
     */
    @AgentExecution(value = "agent1_generate_titles",description = "生成标题方案")
    private void agent1GenerateTitle(ArticleState state) {
        String prompt=PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}",state.getTopic())
                +getStylePrompt(state.getStyle());
        String content=callLlm(prompt);
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){},
                "标题方案"
        );
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功taskId={}",state.getTaskId());
    }
    /**
     * 智能体2：生成大纲(流式输出)
     *
     * @param state
     * @param streamHandler
     */
    @AgentExecution(value = "agent2_generate_outline",description = "生成文章大纲")
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        //构建prompt根据是否有用户补充描述插入对应部分
        String descriptionSection="";
        if(state.getUserDescription()!=null&&state.getUserDescription().trim().isEmpty()){
            descriptionSection=PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}",state.getUserDescription());
        }
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT.
                replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{desciptionSection}",state.getUserDescription())
                +getStylePrompt(state.getStyle());
        //调用智能体2生成大纲
        String content = callLlmWithStream(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：生成大纲生成大纲成功，section={}", outlineResult.getSections().size());
    }
    /**
     * 智能体3生成正文（流式输出）
     *
     * @param state
     * @param streamHandler
     */
    @AgentExecution(value = "agent3_generate_content",description = "生成文章正文")
    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText);
        String content = callLlmWithStream(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成成功，length{}", content.length());
    }
    /**
     * 智能体4：分析配图需求
     *
     * @param state
     */
    /**
     * 智能体4：分析配图需求（在正文中插入占位符）
     */
    @AgentExecution(value = "agent4_analy_image_requirements",description = "分析配图需求")
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        // 构建可用配图方式说明
        String availableMethods = buildAvailableMethodsDescription(state.getEnabledImageMethods());

        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent())
                .replace("{availableMethods}", availableMethods);

        String content = callLlm(prompt);
        ArticleState.Agent4Result agent4Result = parseJsonResponse(
                content,
                ArticleState.Agent4Result.class,
                "配图需求"
        );

        // 更新正文为包含占位符的版本
        state.setContent(agent4Result.getContentWithPlaceholders());
        state.setImageRequirements(agent4Result.getImageRequirements());
        log.info("智能体4：配图需求分析成功, count={}, 已在正文中插入占位符",
                agent4Result.getImageRequirements().size());
    }


    /**
     * 智能体5：生成配图（串行执行，统一上传到cos）
     *
     * @param state
     * @param streamHandler
     */
    @AgentExecution(value = "agent5_generate_images", description = "生成配图")
    public void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始获取配图, position={}, imageSource={}, keywords={}",
                    requirement.getPosition(), imageSource, requirement.getKeywords());

            // 构建图片请求对象
            ImageRequest imageRequest = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    .build();

            // 使用策略模式获取图片并统一上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);

            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 创建配图结果（URL 已经是 COS 地址）
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：配图获取并上传成功, position={}, method={}, cosUrl={}",
                    requirement.getPosition(), method.getValue(), cosUrl);
        }

        state.setImages(imageResults);
        log.info("智能体5：所有配图生成并上传完成, count={}", imageResults.size());
    }
    /**
     * 调用LLM（非流式）
     *
     * @param prompt
     * @return
     */
    private String callLlm(String prompt) {
        //调用千问大模型
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));

        //从ai回答的完整结果里面提取出文本内容
        return response.getResult().getOutput().getText();
    }

    /**
     * 流式调用LLM
     *
     * @param prompt
     * @param streamHandler
     * @param messageType
     * @return
     */
    private String callLlmWithStream(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType)  {
        StringBuilder contentBuilder = new StringBuilder();
        //调用AI模型，发送提示词，获取【流式】响应结果
        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));
        // 3. 处理Flux数据流（AI每吐一段文字，就执行一次）
        streamResponse
                .doOnNext(response -> {
                    // 从AI返回的结果中，提取【本次返回的文字片段】
                    //String chunk =响应对象.取结果().取AI消息().取文本内容();
                    String chunk = response.getResult().getOutput().getText();
                    // 判断：片段不为空、不是纯空格，才处理
                    if (chunk != null && !chunk.trim().isEmpty()) {
                        // 把片段拼接到总内容里（最后返回完整文本用）
                        contentBuilder.append(chunk);
                        // 把片段【实时推送给前端】，实现打字机效果
                        // messageType.getStreamingPrefix() → 给消息加类型标记（如 OUTLINE:、CONTENT:）
                        streamHandler.accept(messageType.getStreamingPrefix() + chunk);
                    }
                }).doOnError(error -> log.error("LLM流式调用失败，messageType:{}，error:{}", messageType, error))
                .blockLast();
        return contentBuilder.toString();
    }
    /**
     * 解析JSON响应----将json字符串解析为指定类型的对象
     *
     * @param content
     * @param clazz
     * @param name
     * @param <T>
     * @return
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        //解析json字符串为对象
        try {
            //自定义的GsonUtils解析json字符串为对象
            return GsonUtils.fromJson(content, clazz);
        } catch (Exception e) {
            log.error("{}解析json字符串为对象失败，content={}", name, content, e);
            throw new RuntimeException(name + "解析失败", e);
        }
    }

    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken.getType());
        } catch (Exception e) {
            log.error("{}解析json字符串为对象失败，content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId());  // 记录占位符ID
        return imageResult;
    }


    /**
     * 图文生成：将配图插入正文对应位置
     * @param state
     */
    @AgentExecution(value = "agent6_merge_content",description = "图文合成")
    private void mergeImageIntoContent(ArticleState state){
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();
        if(images == null || images.isEmpty()){
            state.setFullContent(content);
            return;
        }
        StringBuilder fullContent = new StringBuilder();
        //按行处理正文，再章节标题后插入对应图片
        String[] lines = content.split("\n");
        for (String line : lines ) {
            fullContent.append(line).append("\n");

            //检查是否是章节标题（以##开头）
            if(line.startsWith("##")){
                String sectionTitle = line.substring(3).trim();
                //在章节后面插入图片
                insertImageAfterSection(fullContent, images, sectionTitle);
            }
        }
        state.setFullContent(fullContent.toString());
        log.info("图文合成完成，fullContent={}", fullContent.length());
    }

    /**
     * 在章节后插入对应图片
     * @param fullContent
     * @param images
     * @param sectionTitle
     */
    //contains：判断一个字符串里，有没有包含另一段文字
    private void insertImageAfterSection(StringBuilder fullContent,
                                         List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if(image.getPosition()>1&&image.getSectionTitle()!=null
                    &&sectionTitle.contains(image.getSectionTitle().trim())
            ){
                fullContent.append("\n![")
                        .append(image.getDescription())
                        .append("](")
                        .append(image.getUrl())
                        .append(")\n");
                break;
            }
        }
    }

    /**
     *AI修改大纲
     * @param mainTitle   主标题
     * @param subTitle   副标题
     * @param currentOutline    当前大纲
     * @param modifySuggestion  用户修改建议
     * @return 修改后的大纲
     */
    @AgentExecution(value = "ai_mpdify_outline",description = "AI修改大纲")
    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle,String subTitle,
                                                             List<ArticleState.OutlineSection> currentOutline,
                                                             String modifySuggestion) {
        String currentOutlineJson = GsonUtils.toJson(currentOutline);
        String prompt=PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{currentOutlineJson}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);
        String content = callLlm(prompt);
        //讲json转换为对象
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content,
                ArticleState.OutlineResult.class, "修改后的大纲");
        log.info("AI修改大纲，sectionCount={}",outlineResult.getSections().size());
        return outlineResult.getSections();
    }

    /**
     * 根据风格获取对应的 Prompt 附加内容
     *
     * @param style 文章风格
     * @return 风格对应的 Prompt 附加内容，如果无风格则返回空字符串
     */
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }

        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }

        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }
    /**
     * 图文合成：根据占位符将配图插入正文
     */
    private void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();

        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }

        String fullContent = content;

        // 遍历所有配图，根据占位符替换为实际图片
        for (ArticleState.ImageResult image : images) {
            String placeholder = image.getPlaceholderId();
            if (placeholder != null && !placeholder.isEmpty()) {
                String imageMarkdown = "![" + image.getDescription() + "](" + image.getUrl() + ")";
                fullContent = fullContent.replace(placeholder, imageMarkdown);
            }
        }

        state.setFullContent(fullContent);
        log.info("图文合成完成, fullContentLength={}", fullContent.length());
    }
    /**
     * 构建可用配图方式说明
     */
    private String buildAvailableMethodsDescription(List<String> enabledMethods) {
        // 如果为空或 null，表示支持所有方式
        if (enabledMethods == null || enabledMethods.isEmpty()) {
            return getAllMethodsDescription();
        }

        // 只描述允许的方式
        StringBuilder sb = new StringBuilder();
        for (String method : enabledMethods) {
            ImageMethodEnum methodEnum = ImageMethodEnum.getByValue(method);
            if (methodEnum != null && !methodEnum.isFallback()) {
                sb.append("   - ").append(methodEnum.getValue())
                        .append(": ").append(getMethodUsageDescription(methodEnum))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取所有配图方式的完整描述
     */
    private String getAllMethodsDescription() {
        return """
               - PEXELS: 适合真实场景、产品照片、人物照片、自然风景等写实图片
               - NANO_BANANA: 适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片
               - MERMAID: 适合流程图、架构图、时序图、关系图、甘特图等结构化图表
               - ICONIFY: 适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）
               - EMOJI_PACK: 适合表情包、搞笑图片、轻松幽默的配图
               - SVG_DIAGRAM: 适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）
               """;
    }
    /**
     * 获取配图方式的使用说明
     */
    private String getMethodUsageDescription(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "适合真实场景、产品照片、人物照片、自然风景等写实图片";
            case NANO_BANANA -> "适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片";
            case MERMAID -> "适合流程图、架构图、时序图、关系图、甘特图等结构化图表";
            case ICONIFY -> "适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）";
            case EMOJI_PACK -> "适合表情包、搞笑图片、轻松幽默的配图";
            case SVG_DIAGRAM -> "适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）";
            default -> method.getDescription();
        };
    }
}

