package com.ysw.aipassagecreator.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ysw.aipassagecreator.config.PexelsConfig;
import com.ysw.aipassagecreator.model.enums.ImageMethodEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.ysw.aipassagecreator.constant.ArticleConstant.*;

/**
 * pexels图片检索服务
 */
@Service
@Slf4j
public class PexelsService implements ImageSearchService {
    @Resource
    private PexelsConfig pexelsConfig;
    //OkHttpClient：Java 发送 HTTP 请求的工具（去调用 Pexels 接口）
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public String searchImage(String keywords) {
        try {
            String url = buildSearchUrl(keywords);
            // 2. 构建请求（带上 API 密钥）
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", pexelsConfig.getApiKey())
                    .build();
            // 3. 发送请求--execute()执行！发送请求,newCall(request)包装成通话任务
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Pexels API 调用失败: {}", response.code());
                    return null;
                }
                // 4. 获取返回结果
                String responseBody = response.body().string();

                // 5. 从结果里解析出图片地址
                return extractImageUrl(responseBody, keywords);
            }
        } catch (IOException e) {
            log.error("Pexels API 调用异常", e);
            return null;
        }
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.PEXELS;
    }

    @Override
    public String getFallbackImage(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

    /**
     * 构建搜索 URL
     *
     * @param keywords 搜索关键词
     * @return 完整的搜索 URL
     */
    private String buildSearchUrl(String keywords) {
        return String.format("%s?query=%s&per_page=%d&orientation=%s",
                PEXELS_API_URL,//Pexels API 地址
                keywords,
                PEXELS_PER_PAGE,//Pexels 每页返回数量
                PEXELS_ORIENTATION_LANDSCAPE);//
    }
    private String extractImageUrl(String responseBody, String keywords) {
        //把 Pexels 返回的那一大串 JSON 字符串，
        //解析成 Java 可以读取、可以操作的 JSON 对象。
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray photos = jsonObject.getAsJsonArray("photos");
        if(photos.isEmpty()){
            log.warn("Pexels 未检索到图片：{}",keywords);
            return null;
        }
        JsonObject photo = photos.get(0).getAsJsonObject();
        JsonObject src = photo.getAsJsonObject("src");
        return src.get("large").getAsString();
    }

}
