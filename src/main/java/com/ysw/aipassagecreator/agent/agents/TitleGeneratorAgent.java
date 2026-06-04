package com.ysw.aipassagecreator.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.reflect.TypeToken;
import com.ysw.aipassagecreator.constant.PromptConstant;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.entity.Article;
import com.ysw.aipassagecreator.model.enums.ArticleStyleEnum;
import com.ysw.aipassagecreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 标题生成Agent
 * 根据选题上生成3-5个暴改标题方案
 */
@Component
@Slf4j
@RequiredArgsConstructor
//NodeAction--你写的这个类（标题生成），想要放进 StateGraph（流程图）里当一个节点，
//就必须遵守这个规矩 —— 实现 NodeAction 接口。
//流程图跑到这个节点时，会自动调用 apply () 方法！
public class TitleGeneratorAgent implements NodeAction{
    private final DashScopeChatModel dashScopeChatModel;

    public static final String INPUT_TOPIC="topic";
    public static final String INPUT_STYLE="style";
    public static final String OUTPUT_TITLE_OPTIONS="titleOptions";
    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic=state.value(INPUT_TOPIC)// 1. 取值（Optional）
                .map(Object::toString)// 2. 转字符串
                .orElseThrow(()->new IllegalArgumentException("缺少选题参数"));// // 3. 没值就抛异常

        String style=state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        log.info("TitleGeneratorAgent 开始执行 topic:{} style:{}",topic,style);

        //构建prompt
        String prompt= PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}",topic)+getStylePrompt(style);

        //调用LLM
        ChatResponse response=chatModel.call(new Prompt(new UserMessage(prompt)));
        String content = response.getResult().getOutput().getText();
        //response：整个响应大包裹
        //.getResult()：拿到结果
        //.getOutput()：拿到输出内容
        //.getText()：拿到最终的纯文本回答

        //解析结果
        List<ArticleState.TitleOption> titleOptions= GsonUtils.fromJson(content,
                new TypeToken<List<ArticleState.TitleOption>>(){}
                );

        log.info("TitleGeneratorAgent执行完成：生成了：{}个方案",titleOptions.size());

        return Map.of(OUTPUT_TITLE_OPTIONS,titleOptions);//存进map，map.of相当于put
    }

    /**
     * 根据风格获取对应Prompt附加内容
     */
    private String getStylePrompt(String style){
        if(style==null||style.isEmpty()){
            return "";
        }
        ArticleStyleEnum styleEnum=ArticleStyleEnum.getEnumByValue(style);
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
}
