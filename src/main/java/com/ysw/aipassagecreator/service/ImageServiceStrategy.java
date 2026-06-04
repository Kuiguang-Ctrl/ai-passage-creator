package com.ysw.aipassagecreator.service;


import com.ysw.aipassagecreator.model.dto.image.ImageData;
import com.ysw.aipassagecreator.model.dto.image.ImageData;
import com.ysw.aipassagecreator.model.dto.image.ImageRequest;
import com.ysw.aipassagecreator.model.dto.image.ImageRequest;
import com.ysw.aipassagecreator.model.enums.ImageMethodEnum;
import com.ysw.aipassagecreator.model.enums.ImageMethodEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
//策略模式

/**
 * 图片服务策略选择器
 * 根据图片来源类型选择对应的图片服务实现
 * <p>
 * 设计说明：
 * - 自动注册所有 ImageSearchService 实现
 * - 根据 ImageMethodEnum 的元数据自动选择正确的参数
 * - 支持服务可用性检查和自动降级
 * - 统一处理图片上传到 COS
 */
@Service
@Slf4j
public class ImageServiceStrategy {
    @Resource
    //把所有实现这个接口的类装到这个List里面，这样新增图片检索方法也不用修改接口
    private List<ImageSearchService> imageSearchServices;
    @Resource
    private CosService cosService;
    /**
     * 图片映射服务：：ImageMethodEnum -> ImageSearchService
     */
    /**
     * EnumMap 专门给枚举设计，速度极快（数组实现）
     * 内存占用更小
     * 有序（按枚举定义顺序）
     * 线程不安全，但比 HashMap 高效
     */
    //策略模式的实现---根据不同的图片搜索类型，自动路由到对应的服务类。
    private final Map<ImageMethodEnum, ImageSearchService> serviceMap = new EnumMap<>(ImageMethodEnum.class);

    @PostConstruct
    public void init() {
        //将所有的ImageSearchService
        for (ImageSearchService service : imageSearchServices) {
            ImageMethodEnum method = service.getMethod();
            serviceMap.put(method, service);
            log.info("注册图片服务: {} -> {} (AI生图: {}, 降级: {})",
                    method.getValue(),
                    service.getClass().getSimpleName(),
                    method.isAiGenerated(),
                    method.isFallback());
        }
    }

    /**
     * 获取图片并上传到cos
     * 统一处理所有图片来源的上传逻辑
     * @param imageSource 图片来源
     * @param request  图片请求对象
     * @return  图片获取结果（包含cos URL)
     */
    public ImageResult getImageAndUpload(String imageSource, ImageRequest request) {
        ImageMethodEnum method=resolveMethod(imageSource);//解析图片来源，处理未知值
        ImageSearchService service = serviceMap.get(method);
        if(service==null){
            log.warn("图片服务不可用：{},尝试降级",method);
            return handleFallbackWithUpload(request.getPosition());
        }
        try {
            //获取图片数据
            ImageData imageData = service.getImageData(request);
            if (imageData == null || !imageData.isValid()) {
                log.warn("图片获取失败使用降级方案method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }


            //上传到cos
            String folder = getFolderMethod(method);
            String cosUrl = cosService.uploadImageData(imageData, folder);
            if (cosUrl != null && !cosUrl.isEmpty()) {
                log.info("图片获取并上传成功, method={}, cosUrl={}", method, cosUrl);
                return new ImageResult(cosUrl, method);
            } else {
                log.warn("图片上传 COS 失败, 使用降级方案, method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }
        } catch (Exception e) {
            log.error("获取图片并上传异常, method={}", method, e);
            return handleFallbackWithUpload(request.getPosition());
        }
        }



    /**
     * 根据图片方式获取cos文件夹
     */
    private  String getFolderMethod(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "pexels";
            case NANO_BANANA -> "nano-banana";
            case MERMAID -> "mermaid";
            case ICONIFY -> "iconify";
            case EMOJI_PACK -> "emoji-pack";
            case SVG_DIAGRAM -> "svg-diagram";
            case PICSUM -> "picsum";
        };
    }


    /**
     * 解析图片来源，处理未知值
     */
    private ImageMethodEnum resolveMethod(String imageSource){
        ImageMethodEnum method = ImageMethodEnum.getByValue(imageSource);
        if (method==null){
            log.warn("未知图片来源：{}，默认使用{}",imageSource,ImageMethodEnum.getDefaultSearchMethod());
            return ImageMethodEnum.getDefaultSearchMethod();
        }
        return method;
    }

    /**
     * 处理降级逻辑（包括上传）
     */
    private ImageResult handleFallbackWithUpload(Integer position) {
        int pos=position!=null?position:1;
        String fallbackUrl=getFallbackImage(pos);

        //将降级图片也传到cos
        ImageData fallbackData = ImageData.fromUrl(fallbackUrl);
        String cosUrl=cosService.uploadImageData(fallbackData,"fallback");//上传cos

        //如果上传失败直接用原始URL
        String finalUrl=(cosUrl!=null&&!cosUrl.isEmpty())?cosUrl:fallbackUrl;
        return new ImageResult(finalUrl, ImageMethodEnum.getFallbackMethod());
    }
    /**
     * 获取指定方法的图片服务
     */
    public ImageSearchService getService(ImageMethodEnum method) {
        return serviceMap.get(method);
    }


    /**
     * 获取降级图片
     */
    public String getFallbackImage(int position) {
        ImageSearchService defaultService = serviceMap.get(ImageMethodEnum.getDefaultSearchMethod());
        if(defaultService!=null){
            return defaultService.getFallbackImage(position);
        }
        return String.format("https://picsum.photos/800/600?random=%d", position);
    }
    /**
     * 获取所有已注册的图片服务类型
     */
    public List<ImageMethodEnum> getRegistereMethods() {
        return List.copyOf(serviceMap.keySet());
    }


    /**
     * 图片获取结果
     */
    public static class ImageResult {
        private final String url;
        private final ImageMethodEnum method;

        public ImageResult(String url, ImageMethodEnum method) {
            this.url = url;
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public ImageMethodEnum getMethod() {
            return method;
        }

        public boolean isSuccess() {
            return url != null && !url.isEmpty();
        }
    }

}


