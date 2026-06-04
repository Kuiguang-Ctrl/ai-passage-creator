package com.ysw.aipassagecreator;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import java.io.ByteArrayInputStream;

public class CosTest {
    public static void main(String[] args) {
        // 从环境变量读取，避免密钥写入代码
        String secretId = System.getenv("TENCENT_COS_SECRET_ID");
        String secretKey = System.getenv("TENCENT_COS_SECRET_KEY");
        String bucket = "ai-passage-creator-1434558359";
        String region = "ap-guangzhou";

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        COSClient cosClient = new COSClient(cred, clientConfig);

        try {
            // 上传一个简单的文本文件
            byte[] testData = "hello cos test".getBytes();
            String key = "test/debug.txt";

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucket, key, new ByteArrayInputStream(testData), null);

            cosClient.putObject(putObjectRequest);
            System.out.println("✅ 上传成功！文件路径：" + key);
        } catch (Exception e) {
            System.err.println("❌ 上传失败：" + e.getMessage());
            e.printStackTrace();
        } finally {
            cosClient.shutdown();
        }
    }
}