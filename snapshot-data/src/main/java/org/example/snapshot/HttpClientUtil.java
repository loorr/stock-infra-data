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
 * tcp ����
 * TODO �ػ�
 */
@Log4j2
public class HttpClientUtil {
    /**
     * ��ʱʱ��
     */
    private static final int TIMEOUT = 30 * 1000;

    /**
     * ���������
     */
    private static final int MAX_TOTAL = 200;

    /**
     * ÿ��·�ɵ�Ĭ�����������
     */
    private static final int MAX_PER_ROUTE = 400;

    /**
     * Ŀ�����������������
     */
    private static final int MAX_ROUTE = 100;

    /**
     * ����ʧ��ʱ������Դ���
     */
    private static final int MAX_RETRY_TIME = 5;

    private static CloseableHttpClient httpClient = null;
    private static final Object SYNC_LOCK = new Object();
    private static final String DEFAULT_CHARSET = "UTF-8";

    private static void config(HttpRequestBase httpRequestBase) {
        //��������ĳ�ʱʱ��
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(TIMEOUT)
                .setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT)
                .build();
        httpRequestBase.setConfig(requestConfig);
    }

    /**
     * ��ȡHttpClient����
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
     * ����HttpClient����
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
        //�������������
        cm.setMaxTotal(maxTotal);
        //����ÿ��·�ɵ�Ĭ���������
        cm.setDefaultMaxPerRoute(maxPerRoute);
        //����Ŀ�����������������
        cm.setMaxPerRoute(new HttpRoute(new HttpHost(hostName, port)), maxRoute);
        //��������
        HttpRequestRetryHandler httpRequestRetryHandler = (exception, executionCount, context) -> {
            //������5�Σ�����
            if (executionCount >= MAX_RETRY_TIME) {
                return false;
            }
            //�����������������ӣ��Ǿ�����
            if (exception instanceof NoHttpResponseException) {
                return true;
            }
            //������SSL�����쳣
            if (exception instanceof SSLHandshakeException) {
                return false;
            }
            //��ʱ
            if (exception instanceof InterruptedIOException) {
                return false;
            }
            //Ŀ����������ɴ�
            if (exception instanceof UnknownHostException) {
                return false;
            }
            //SSL�����쳣
            if (exception instanceof SSLException) {
                return false;
            }
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            //������ʱ�ݵȵģ����ٴγ���
            return !(request instanceof HttpEntityEnclosingRequest);
        };
        return HttpClients.custom().setConnectionManager(cm)
                .setRetryHandler(httpRequestRetryHandler)
                .build();
    }

    /**
     *     HttpClient����SSL�ƹ�https֤�飨��Ϊ�ҵ���վ����https֤��ģ������ڷ���https��վʱ�����Զ���ȡ�ҵ�֤�飬
     * ��Ŀ����վ�������ᱨ��������������Ҫ�ƹ�https֤��
     */
    private static SSLContext createIgnoreVerifySSL() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("SSLv3");
        // ʵ��һ��X509TrustManager�ӿڣ������ƹ���֤�������޸�����ķ���
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
     * post����Ĭ�ϱ����ʽΪUTF-8
     * @param url �����ַ
     * @param params �������
     * @return ��Ӧ����
     */
    public static String doPost(String url, Map<String, Object> params) {
        return doPost(url, params, DEFAULT_CHARSET);
    }

    /**
     * post����
     * @param url �����ַ
     * @param params �������
     * @param charset �ַ�����
     * @return ��Ӧ����
     */
    public static String doPost(String url, Map<String, Object> params, String charset) {
        HttpPost httpPost = new HttpPost(url);
        config(httpPost);
        setPostParams(httpPost, params);
        return getResponse(url, httpPost, charset);
    }

    /**
     * get����Ĭ�ϱ���UTF-8
     * @param url �����ַ
     * @return ��Ӧ����
     */
    public static String doGet(String url) {
        return doGet(url, DEFAULT_CHARSET);
    }

    /**
     * get����
     * @param url �����ַ
     * @param charset �ַ�����
     * @return ��Ӧ����
     */
    public static String doGet(String url, String charset) {
        HttpGet httpGet = new HttpGet(url);
        config(httpGet);
        return getResponse(url, httpGet, charset);
    }

    /**
     * �������󣬻�ȡ��Ӧ
     * @param url �����ַ
     * @param httpRequest �������
     * @param charset �ַ�����
     * @return ��Ӧ����
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
            log.error("��������쳣��", e);
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