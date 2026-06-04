package com.ysw.aipassagecreator.service.impl;

import cn.hutool.core.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ysw.aipassagecreator.exception.BusinessException;
import com.ysw.aipassagecreator.exception.ErrorCode;
import com.ysw.aipassagecreator.exception.ThrowUtils;
import com.ysw.aipassagecreator.mapper.ArticleMapper;
import com.ysw.aipassagecreator.model.dto.article.ArticleQueryRequest;
import com.ysw.aipassagecreator.model.dto.article.ArticleState;
import com.ysw.aipassagecreator.model.entity.Article;
import com.ysw.aipassagecreator.model.entity.User;
import com.ysw.aipassagecreator.model.enums.ArticlePhaseEnum;
import com.ysw.aipassagecreator.model.enums.ArticleStatusEnum;
import com.ysw.aipassagecreator.model.enums.ImageMethodEnum;
import com.ysw.aipassagecreator.model.vo.ArticleVO;
import com.ysw.aipassagecreator.service.ArticleAgentService;
import com.ysw.aipassagecreator.service.ArticleService;
import com.ysw.aipassagecreator.service.QuotaService;
import com.ysw.aipassagecreator.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.ysw.aipassagecreator.constant.UserConstant.ADMIN_ROLE;
import static com.ysw.aipassagecreator.constant.UserConstant.VIP_ROLE;

@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {
/*
    private final ArticleService articleService;
    private final ArticleAgentService articleAgentService;*/
    @Resource
    private QuotaService quotaService;
    @Resource
    private ArticleAgentService articleAgentService;

  /*  public ArticleServiceImpl(ArticleService articleService, ArticleAgentService articleAgentService) {
        this.articleService = articleService;
        this.articleAgentService = articleAgentService;
    }*/

/*    @Override
    public String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 处理配图方式：如果用户未选择，给普通用户设置默认的非 VIP 方式
        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);

        // 校验配图方式权限（普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM）
        validateImageMethods(finalImageMethods, loginUser);

        // 生成任务ID
        String taskId = IdUtil.simpleUUID();

        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStyle(style);
        article.setEnabledImageMethods(finalImageMethods != null && !finalImageMethods.isEmpty()
                ? GsonUtils.toJson(finalImageMethods) : null);
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setPhase(ArticlePhaseEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());

        this.save(article);

        log.info("文章任务已创建, taskId={}, userId={}, style={}", taskId, loginUser.getId(), style);
        return taskId;
    }*/
@Override
@Transactional(rollbackFor = Exception.class)
public String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser) {
    // 在同一事务中：先扣配额，再创建任务
    // 如果任务创建失败，配额会自动回滚
    quotaService.checkAndConsumeQuota(loginUser);
    return createArticleTask(topic, style, enabledImageMethods, loginUser);
}


    public String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser) {
        // 处理配图方式：如果用户未选择，给普通用户设置默认的非 VIP 方式
        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);

        // 校验配图方式权限（普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM）
        validateImageMethods(finalImageMethods, loginUser);

        //生成任务ID
        String taskId= IdUtil.simpleUUID();
        //创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setTopic(topic);
        article.setUserId(loginUser.getId());
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());
        this.save(article);
        log.info("文章任务已创建，taskId={}，userId={}", taskId, loginUser.getId());
        return taskId;
    }
    public Article getByTaskId(String taskId){
        return this.getOne(QueryWrapper.create().eq("taskId",taskId));
    }
    /**
     * 更新状态
     */
    public void updateArticleStatus(String taskId, ArticleStatusEnum status,String errorMessage){
        Article article = this.getByTaskId(taskId);
        if(article==null){
            log.error("文章记录不存在，taskId={}",taskId);
            return;
        }
        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);

        log.info("文章状态已更新，taskId={},status={}",taskId,status);
    }
    /**
     * 保存文章内容
     */
    public void saveArticleContent(String taskId, ArticleState state){
        Article article = this.getByTaskId(taskId);
        if(article==null){
            log.info("文章不存在taskId={}",taskId);
            return;
        }
        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        //Outline是list数据库补能存集合
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());
        //保存封面图URL(从images列表里面提取出position=1的URL)
        if(state.getImages()!=null&& !state.getImages().isEmpty()){
            ArticleState.ImageResult cover = state.getImages().stream().filter(img -> img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if(cover!=null&&cover.getUrl()!=null){
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章保存成功，taskId={}",taskId);
    }

    /**
     * 获取文章详情
     * @param taskId    任务ID
     * @param loginUser 当前登录用户
     * @return
     */
    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = this.getByTaskId(taskId);
        ThrowUtils.throwIf(article==null,ErrorCode.NOT_FOUND_ERROR,"文章不存在");
        //校验权限只能查看自己的文章（管理员除外）
        checkArticlePermission(article,loginUser);
        return ArticleVO.objToVo(article);
    }

    /**
     * 分页查询方法
     */
    public Page<ArticleVO> listArticleBypage(ArticleQueryRequest request,User loginUser){
        int current = request.getPageNum();
        int size = request.getPageSize();
        //构键查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete",0)//未删除
                .orderBy("createTime",false);//倒序
        //非管理员只能查看自己的文章
        if(!ADMIN_ROLE.equals(loginUser.getUserRole())){
            queryWrapper.eq("userId",loginUser.getId());
        }else if (request.getUserId()!=null){
            //求情参数里面的用户id
            queryWrapper.eq("userId",request.getUserId());
        }
        //上面那些都是在构造器里面加条件


        //按状态筛选
        if(request.getStatus()!=null&& request.getStatus().trim().isEmpty()){
            queryWrapper.eq("status",request.getStatus());
        }
        //分页查询
        Page<Article> articlePage = this.page(new Page<>(current,size), queryWrapper);
        //转换为 VO
        return convertToVOPage(articlePage);
    }

    /**
     * 删除文章
     * @param id 要删除的文章
     * @param loginUser 用户自己的
     * @return
     */
    public boolean deleteArticle(Long id,User loginUser){
        Article article = this.getById(id);
        ThrowUtils.throwIf(article==null, ErrorCode.NOT_FOUND_ERROR);
        //验证权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article,loginUser);
        return this.removeById(id);

    }


    /**
     * 将文章分页结果转换为 VO 分页
     *
     * @param articlePage 文章分页
     * @return VO 分页
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }
    /**
     * 校验文章权限
     *
     * @param article   文章
     * @param loginUser 当前用户
     */
    //当前用户不是文章创建着也不是管理员就报异常
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) &&
                !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
    /**
     * 确认标题（用户选择后）
     *
     * @param taskId       任务ID
     * @param mainTitle    选中的主标题
     * @param subTitle     选中的副标题
     * @param userDescription 用户补充描述
     * @param loginUser    当前登录用户
     */
    @Override
    public void confirmTitle(String taskId, String mainTitle, String subTitle, String userDescription, User loginUser) {
        Article article = this.getByTaskId(taskId);
        ThrowUtils.throwIf(article==null,ErrorCode.NOT_FOUND_ERROR,"文章不存在");
        //校验权限
        checkArticlePermission(article,loginUser);
        //校验当前阶段（必须是TITLE_GENERATING阶段）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase!=ArticlePhaseEnum.TITLE_GENERATING,
                ErrorCode.OPERATION_ERROR,"当前阶段不允许此操作");
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());
        this.updateById(article);
        log.info("用户确认标题，taskId={},mainTitle={}",taskId,mainTitle);
    }
    /**
     * 确认大纲（用户编辑后）
     *
     * @param taskId    任务ID
     * @param outline   用户编辑后的大纲
     * @param loginUser 当前登录用户
     */
    @Override
    public void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article==null,ErrorCode.NOT_FOUND_ERROR,"文章不存在");
        //校验权限
        checkArticlePermission(article,loginUser);
        //校验当前状态（必须是OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase!=ArticlePhaseEnum.OUTLINE_EDITING,
                ErrorCode.OPERATION_ERROR,"但前阶段不允许此操作");
        //保存用户编辑好的的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());
        this.updateById(article);
        log.info("用户确认大纲，taskId={},sectionsCount={}",taskId,outline.size());
    }
    /**
     * 更新阶段
     *
     * @param taskId 任务ID
     * @param phase  阶段枚举
     */
    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum phase) {
        Article article = getByTaskId(taskId);
        if(article==null){
            log.error("文章不存在taskId={}",taskId);
            return;
        }
        article.setPhase(phase.getValue());
        this.updateById(article);
        log.info("文章阶段已更新taskId={}，phase={}",taskId,phase);
    }
    /**
     * 保存标题方案
     *
     * @param taskId       任务ID
     * @param titleOptions 标题方案列表
     */
    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions) {
        Article article = getByTaskId(taskId);
        if(article==null){
            log.error("文章不存在taskId={}",taskId);
            return;
        }
        article.setTitleOptions(GsonUtils.toJson(titleOptions));
        this.updateById(article);
        log.info("标题方案以保存，taskId={},titleOptions={}",taskId,titleOptions);
    }
    /**
     * AI 修改大纲
     *
     * @param taskId           任务ID
     * @param modifySuggestion 用户修改建议
     * @param loginUser        当前登录用户
     * @return 修改后的大纲
     */
   public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser){
       Article article = this.getByTaskId(taskId);
       ThrowUtils.throwIf(article==null,ErrorCode.NOT_FOUND_ERROR,"文章不存在");
       //校验权限
       checkArticlePermission(article,loginUser);

       // 校验 VIP 权限（普通用户不能使用 AI 修改大纲）
       ThrowUtils.throwIf(!isVipOrAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,
               "AI 修改大纲功能仅限 VIP 会员使用");
       //校验当前阶段
       ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
       ThrowUtils.throwIf(currentPhase!=ArticlePhaseEnum.OUTLINE_EDITING,
               ErrorCode.OPERATION_ERROR,"当前阶段不允许此操作");
       //获取当前大纲
       List<ArticleState.OutlineSection> currentOutline = GsonUtils.fromJson(
               article.getOutline(),
               new TypeToken<List<ArticleState.OutlineSection>>() {});
       //调用ai修改大纲
       List<ArticleState.OutlineSection> modifyOutline = articleAgentService.aiModifyOutline(article.getMainTitle(),
               article.getSubTitle(), currentOutline, modifySuggestion);
       //保存修改后的大纲
       article.setOutline(GsonUtils.toJson(modifyOutline));
       this.updateById(article);
       log.info("AI大纲生成完成，taskId={},sectionCount={}",taskId,modifyOutline.size());
       return modifyOutline;
   }
    /**
     * 处理配图方式
     * 如果用户未选择，给普通用户设置默认的非VIP方式，VIP用户不受限制
     */
    private List<String> processImageMethods(List<String> enableImageMethods, User loginUser){
        //如果用户已选择，直接返回
        if(enableImageMethods==null || enableImageMethods.isEmpty()){
            return enableImageMethods;
        }
        //VIP和管理员不受限制返回null表示支持所有方式
        if(isVipOrAdmin(loginUser)){
            return null;
        }
        //普通用户返回默认的非VIP方式
        return List.of(
                ImageMethodEnum.PEXELS.getValue(),
                ImageMethodEnum.MERMAID.getValue(),
                ImageMethodEnum.ICONIFY.getValue(),
                ImageMethodEnum.EMOJI_PACK.getValue()
        );
    }



    /**
     * 校验配图方式权限
     * 普通用户不能使用NANO_BANANA和SVG_DIAGRAM
     */
    private void validateImageMethods(List<String> enabledImageMethods,User loginUser){
        if(enabledImageMethods==null || enabledImageMethods.isEmpty()){
            return;
        }
            if(isVipOrAdmin(loginUser)){
                return;
            }
            for(String method:enabledImageMethods){
                if(ImageMethodEnum.NANO_BANANA.getValue().equals(method)||
                   ImageMethodEnum.SVG_DIAGRAM.getValue().equals(method)
                ){
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR,
                            "高级配图功能（AI生图，SVG图表）仅VIP会员使用");
                }
            }
    }
    /**
     * 判断 是否为VIP或者管理员
     */
    private boolean isVipOrAdmin(User user){
        return ADMIN_ROLE.equals(user.getUserRole())||
                VIP_ROLE.equals(user.getUserRole());
    }


}
