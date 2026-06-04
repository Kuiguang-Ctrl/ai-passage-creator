package com.ysw.aipassagecreator.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.ysw.aipassagecreator.agent.context.StreamHandlerContext;
import com.ysw.aipassagecreator.constant.PromptConstant;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.enums.ArticleStyleEnum;
import com.ysw.aipassagecreator.model.enums.SseMessageTypeEnum;
import com.ysw.aipassagecreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.utils.TryConsumer.log;
@Component
@Slf4j
@RequiredArgsConstructor
public class OutlineGeneratorAgent implements NodeAction {


    private final DashScopeChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_USER_DESCRIPTION = "userDescription";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_OUTLINE = "outline";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));

        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        String userDescription = state.value(INPUT_USER_DESCRIPTION)
                .map(Object::toString)
                .orElse(null);

        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        log.info("OutlineGeneratorAgent 开始执行: mainTitle={}, subTitle={}", mainTitle, subTitle);

        //构建用户描述部分
        String descriptionSection = "";
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}", userDescription);
        }

        // 构建 prompt
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{descriptionSection}", descriptionSection)
                + getStylePrompt(style);

        //获取流式处理器
        Consumer<String> streamHandler= StreamHandlerContext.get();

        //调用LLM(流式输出）
        String content=callLlmWithStreaming(prompt,streamHandler);

        //解析结果
        ArticleState.OutlineResult outlineResult= GsonUtils.fromJson(content, ArticleState.OutlineResult.class);

        log.info("OutlineGeneratorAgent执行完毕，生成了{}个章节",outlineResult.getSections().size());
        return Map.of(OUTPUT_OUTLINE, outlineResult);
    }

    /**
     *根据风格获取对应的Prompt附加内容
     */
    private String getStylePrompt(String style) {
        if (style==null || style.trim().isEmpty()) {
            return "";
        }
        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum==null) {
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
     * 调用LLM(流式输出）
     * @param prompt  用户问题
     * @param streamHandler 用来把每一段内容推给前端的“处理器”
     * @return
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler) {
        StringBuilder contentBuilder = new StringBuilder();
        Flux<ChatResponse> streamResponse=chatModel.stream(new Prompt(new UserMessage(prompt)));
        // 3. 开始处理流里的每一段消息
        streamResponse.
                doOnNext(response -> {// 每当 AI 返回一段内容，就执行这里
                    String chunk=response.getResult().getOutput().getText();
                    // 5. 如果片段不为空，才处理（过滤空消息）
                    if (chunk!=null && !chunk.isEmpty()) {
                        // 6. 拼接到总结果里（最后要返回完整答案）
                        contentBuilder.append(chunk);
                        //带前缀发送流式消息
                        if(streamHandler!=null) {
                            // 7. 如果需要推送给前端，就推
                            streamHandler.accept(SseMessageTypeEnum.AGENT2_STREAMING.
                                    getStreamingPrefix()+chunk);//(大纲流式输出+：）
                        }
                    }
                })
                .doOnError(error ->log.error("OutlineGeneratorAgent流式调用失败",error))
                .blockLast();
        return contentBuilder.toString();
    }
    }
