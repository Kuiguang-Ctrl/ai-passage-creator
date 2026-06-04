package com.ysw.aipassagecreator.controller;

import com.mybatisflex.core.paginate.Page;
import com.ysw.aipassagecreator.annotation.AuthCheck;
import com.ysw.aipassagecreator.common.BaseResponse;
import com.ysw.aipassagecreator.common.DeleteRequest;
import com.ysw.aipassagecreator.common.ResultUtils;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.exception.ThrowUtils;
import com.ysw.aipassagecreator.manager.SseEmitterManager;
import com.ysw.aipassagecreator.model.dto.article.*;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.model.enums.ArticleStyleEnum;
import com.ysw.aipassagecreator.model.vo.AgentExecutionStats;
import com.ysw.aipassagecreator.model.vo.ArticleVO;
import com.ysw.aipassagecreator.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Results;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/article")
@Tag(name = "文章接口")
@Slf4j
public class ArticleController {
    @Resource
    private ArticleService articleService;
    @Resource
    private UserService userService;
    @Resource
    private ArticleAsyncService articleAsyncService;
    @Resource
    private SseEmitterManager sseEmitterManager;
    @Resource
    private AgentLogService agentLogService;
    /**
     * 创建文章任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "选题不能为空");
        // 校验风格参数（允许为空）
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()),
                ErrorCode.PARAMS_ERROR, "无效的文章风格");
        //检验权限(内部会检查任务是否存在，以及用户是否有权限访问)
        User loginUser = userService.getLoginUser(httpServletRequest);
        //检查并消耗配额+创建文章任务（同一事物中）
        String taskId=articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(),
                request.getStyle(),
                request.getEnabledImageMethods(),
                loginUser
        );
        //异步执行阶段1：生成标题方案
        articleAsyncService.exectePhase1(
                taskId,
                request.getTopic(),
                request.getStyle()
        );
        return ResultUtils.success(taskId);
    }

    /**
     * 确认标题并输入补充描述
     * @param request
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/confirm-title")
    public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request,
                                           HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null&&request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务id不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null&&request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null&&request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务副标题不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        //确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );
        //异步执行阶段2：生成大纲
        articleAsyncService.exectePhase2(request.getTaskId());
        return ResultUtils.success(null);
    }
    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    public BaseResponse<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request,
                                             HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null&&request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务id不能为空");
        ThrowUtils.throwIf(request.getOutline() == null&&request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务主标题不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);

        //确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );
        //异步任务执行3：生成正文+配图
        articleAsyncService.exectePhase3(request.getTaskId());
        return ResultUtils.success(null);
    }
    /**
     * AI修改大纲
     */
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @RequestBody ArticleAiModifyOutlineRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null&&request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务id不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null&&request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR,"修改建议不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        //AI修改大纲
        List<ArticleState.OutlineSection>modifiedOutline=articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );
        return ResultUtils.success(modifiedOutline);
    }

    /**
     * 建立SSE进度推送接口--只建立接口不推送信息
     * @param taskId
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度（SSE）")
    public SseEmitter getProgess(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null|| taskId.isEmpty(),
                ErrorCode.PARAMS_ERROR,"任务id不能为空");
        //校验权限（内部会检查任务是否存在以及用户是否有权限访问
        User loginUser = userService.getLoginUser(httpServletRequest);
        articleService.getArticleDetail(taskId,loginUser);//文章详情，这个时候校验的

        //创建SSE Emitter
        SseEmitter emitter = sseEmitterManager.createSseEmitter(taskId);
        log.info("SSE连接已建立,taskId:{}",taskId);
        return emitter;
    }

    /**
     * 获取文章详情
     * @return
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null||
                taskId.isEmpty(),ErrorCode.PARAMS_ERROR,"任务id不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId,loginUser);
        return ResultUtils.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Page<ArticleVO>> listArticles(@RequestBody ArticleQueryRequest request,
                                                      HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleBypage(request, loginUser);
        return ResultUtils.success(articleVOPage);
    }
    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null||deleteRequest.getId()==null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean result= articleService.deleteArticle(deleteRequest.getId(),loginUser);
        return ResultUtils.success(result);
    }


    /**
     * 获取任务执行日志
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        AgentExecutionStats stats = agentLogService.getExecutionStats(taskId);
        return ResultUtils.success(stats);
    }


}



