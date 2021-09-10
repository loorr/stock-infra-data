package org.example.snapshot;

import lombok.extern.log4j.Log4j2;
import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * tcp 复用
 * TODO 池化
 */
@Log4j2
public class HttpClientUtil {
    /**
     * 超时时间
     */
    private static final int TIMEOUT = 30 * 1000;

    /**
     * 最大连接数
     */
    private static final int MAX_TOTAL = 200;

    /**
     * 每个路由的默认最大连接数
     */
    private static final int MAX_PER_ROUTE = 400;

    /**
     * 目标主机的最大连接数
     */
    private static final int MAX_ROUTE = 100;

    /**
     * 访问失败时最大重试次数
     */
    private static final int MAX_RETRY_TIME = 5;

    private static CloseableHttpClient httpClient = null;
    private static final Object SYNC_LOCK = new Object();
    private static final String DEFAULT_CHARSET = "UTF-8";

    private static void config(HttpRequestBase httpRequestBase) {
        //配置请求的超时时间
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(TIMEOUT)
                .setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT)
                .build();
        httpRequestBase.setConfig(requestConfig);
    }

    /**
     * 获取HttpClient对象
     */
    private static CloseableHttpClient getHttpClient(String url) throws NoSuchAlgorithmException, KeyManagementException {
        String hostName = url.split("/")[2];
        int port = 80;
        if (hostName.contains(":")) {
            String[] attr = hostName.split(":");
            hostName = attr[0];
            port = Integer.parseInt(attr[1]);
        }
        if (httpClient == null) {
            synchronized (SYNC_LOCK) {
                if (httpClient == null) {
                    httpClient = createHttpClient(MAX_TOTAL, MAX_PER_ROUTE, MAX_ROUTE, hostName, port);
                }
            }
        }
        return httpClient;
    }

    /**
     * 创建HttpClient对象
     */
    private static CloseableHttpClient createHttpClient(int maxTotal, int maxPerRoute, int maxRoute,
                                                        String hostName, int port) throws KeyManagementException, NoSuchAlgorithmException {
        PlainConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(createIgnoreVerifySSL());
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plainsf)
                .register("https", sslsf)
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        //增加最大连接数
        cm.setMaxTotal(maxTotal);
        //增加每个路由的默认最大连接
        cm.setDefaultMaxPerRoute(maxPerRoute);
        //增加目标主机的最大连接数
        cm.setMaxPerRoute(new HttpRoute(new HttpHost(hostName, port)), maxRoute);
        //请求重试
        HttpRequestRetryHandler httpRequestRetryHandler = (exception, executionCount, context) -> {
            //若重试5次，放弃
            if (executionCount >= MAX_RETRY_TIME) {
                return false;
            }
            //若服务器丢掉了连接，那就重试
            if (exception instanceof NoHttpResponseException) {
                return true;
            }
            //不重试SSL握手异常
            if (exception instanceof SSLHandshakeException) {
                return false;
            }
            //超时
            if (exception instanceof InterruptedIOException) {
                return false;
            }
            //目标服务器不可达
            if (exception instanceof UnknownHostException) {
                return false;
            }
            //SSL握手异常
            if (exception instanceof SSLException) {
                return false;
            }
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            //若请求时幂等的，就再次尝试
            return !(request instanceof HttpEntityEnclosingRequest);
        };
        return HttpClients.custom().setConnectionManager(cm)
                .setRetryHandler(httpRequestRetryHandler)
                .build();
    }

    /**
     *     HttpClient配置SSL绕过https证书（因为我的网站是有https证书的，所以在访问https网站时，会自动读取我的证书，
     * 和目标网站不符，会报错），所以这里需要绕过https证书
     */
    private static SSLContext createIgnoreVerifySSL() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("SSLv3");
        // 实现一个X509TrustManager接口，用于绕过验证，不用修改里面的方法
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        sslContext.init(null, new TrustManager[] {trustManager}, null);
        return sslContext;
    }

    private static void setPostParams(HttpPost httpPost, Map<String, Object> params) {
        List<NameValuePair> nameValuePairs = new ArrayList<>();
        params.forEach((key, value) -> nameValuePairs.add(new BasicNameValuePair(key, value.toString())));
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, DEFAULT_CHARSET));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * post请求，默认编码格式为UTF-8
     * @param url 请求地址
     * @param params 请求参数
     * @return 响应正文
     */
    public static String doPost(String url, Map<String, Object> params) {
        return doPost(url, params, DEFAULT_CHARSET);
    }

    /**
     * post请求
     * @param url 请求地址
     * @param params 请求参数
     * @param charset 字符编码
     * @return 响应正文
     */
    public static String doPost(String url, Map<String, Object> params, String charset) {
        HttpPost httpPost = new HttpPost(url);
        config(httpPost);
        setPostParams(httpPost, params);
        return getResponse(url, httpPost, charset);
    }

    /**
     * get请求，默认编码UTF-8
     * @param url 请求地址
     * @return 响应正文
     */
    public static String doGet(String url) {
        return doGet(url, DEFAULT_CHARSET);
    }

    /**
     * get请求
     * @param url 请求地址
     * @param charset 字符编码
     * @return 响应正文
     */
    public static String doGet(String url, String charset) {
        HttpGet httpGet = new HttpGet(url);
        config(httpGet);
        return getResponse(url, httpGet, charset);
    }

    /**
     * 发起请求，获取响应
     * @param url 请求地址
     * @param httpRequest 请求对象
     * @param charset 字符编码
     * @return 响应正文
     */
    private static String getResponse(String url, HttpRequestBase httpRequest, String charset) {
        CloseableHttpResponse response = null;
        try {
            response = getHttpClient(url).execute(httpRequest, HttpClientContext.create());
            HttpEntity httpEntity = response.getEntity();
            String result = EntityUtils.toString(httpEntity, charset);
            EntityUtils.consume(httpEntity);
            return result;
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            log.error("网络访问异常！", e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}