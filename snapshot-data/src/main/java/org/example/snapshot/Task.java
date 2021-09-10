package org.example.snapshot;

import com.alibaba.fastjson.JSONObject;
import com.sun.org.slf4j.internal.LoggerFactory;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Date;

@Log4j2
public class Task {
    private static CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    private static final String url = "http://api.money.126.net/data/feed/1000001,1000002,1000881,0601398,1002392,1000877,1000612,1002211,0000001,0600795,0601857,0601600,0601398,0600028,0600019,0601318,0600030,0601939,0601088,0600900,1002024,0600031,1002411,0603351,1002680,1002433,0688091,money.api";
    // 创建Get请求
    private static final HttpGet httpGet = new HttpGet(url); // 响应模型


    public static void taskItem(){
        CloseableHttpResponse response = null;
//        try {
            // 由客户端执行(发送)Get请求
            // response = httpClient.execute(httpGet);
            //response = HttpClientUtil.doGet(url);
            // 从响应模型中获取响应实体
            //HttpEntity responseEntity = response.getEntity();

            //if (200!= response.getStatusLine().getStatusCode()){
            //    log.error("ERROR");
            //    return;
            //}
            //if (responseEntity != null) {
            //    String ans = EntityUtils.toString(responseEntity);
            String ans = HttpClientUtil.doGet(url);
            parsingData(ans);
            //}
//        } catch (ClientProtocolException e) {
//            e.printStackTrace();
//        } catch (ParseException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public static void parsingData(String data){
        String removeBracket = patternContent(data);
        JSONObject jsonObject = (JSONObject) JSONObject.parse(removeBracket);

        for (String key: jsonObject.keySet()){
            StockSnapshot snapshot = jsonObject.getObject(key, StockSnapshot.class);
            // log.info(new Date() + " " +  snapshot);
            SnapshotProcessor.persistentData(snapshot);
        }
    }

    public static String patternContent(String input){
        String[] s = input.split("\\(|\\)");
        if (s.length != 3) {
            return null;
        }
        return s[1];
    }
}
