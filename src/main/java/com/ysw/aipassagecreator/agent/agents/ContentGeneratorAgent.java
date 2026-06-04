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

import static cn.hutool.poi.excel.sax.ElementName.v;

/**
 * 正文生成 Agent
 * 根据大纲生成文章正文内容（支持流式输出）
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_OUTLINE = "outline";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_CONTENT = "content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));

        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        @SuppressWarnings("unchecked")  // 1. 告诉编译器：别警告我强制类型转换
        ArticleState.OutlineResult outline = state.value(INPUT_OUTLINE)  // 2. 从状态里拿【大纲】,这个状态是全局的全局状态
                .map(v -> {  // 3. 如果拿到值，进入 map 处理
                    if (v instanceof ArticleState.OutlineResult) {  // 4. 如果已经是目标类型，直接强转
                        return (ArticleState.OutlineResult) v;
                    }
                    // 5. 如果不是，转 JSON 再转回目标类型（兼容不同存储格式）
                    return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
                })
                .orElseThrow(() -> new IllegalArgumentException("缺少大纲参数"));  // 6. 没有值 → 抛错

        String style=state.value(INPUT_STYLE)
                        .map(Object::toString)
                        .orElse(null);
        log.info("ContentGeneratorAgent开始执行：mainTitle={}", mainTitle);

        //构建prompt
        String outlineText=GsonUtils.toJson(outline.getSections());
        String prompt= PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{outline}", outlineText)
                +getStylePrompt(style);
        //获取流式处理器
        Consumer<String> streamHandler= StreamHandlerContext.get();

        //调用流式输出
        String conent = callLlmWithStreaming(prompt, streamHandler);
        log.info("ContentGeneratorAgent执行完毕:正文长度={}", conent.length());
        return Map.of(OUTPUT_CONTENT, conent);

    }

    /**
     * 根据风格获取对应的Prompt附加内容
     * @param style
     * @return
     */
    private String getStylePrompt(String style) {
    if(style==null||style.isEmpty()){
        return "";
    }
    ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
    if(styleEnum==null){
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
     * 调用流式输出
     */
    private String callLlmWithStreaming(String prompt,Consumer<String> streamHandler){
        StringBuilder contentBuilder=new StringBuilder();

        Flux<ChatResponse> streamResponse= chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response->{
                    String chunk=response.getResult().getOutput().getText();
                    if(chunk!=null&&!chunk.isEmpty()){
                        contentBuilder.append(chunk);
                        //前缀发送
                        if(streamHandler!=null){
                            streamHandler.accept(SseMessageTypeEnum.AGENT3_STREAMING.
                                    getStreamingPrefix()+chunk);
                        }
                    }
                }).doOnError(error->log.error("ContentGeneratorAgent流式调用失败",error))
                .blockLast();

        return contentBuilder.toString();
    }

}