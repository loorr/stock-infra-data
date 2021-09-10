//package org.example.snapshot;
//
//import com.alibaba.fastjson.JSONObject;
//import lombok.extern.log4j.Log4j2;
//import org.apache.http.*;
//import org.apache.http.client.HttpRequestRetryHandler;
//import org.apache.http.client.config.RequestConfig;
//import org.apache.http.client.entity.UrlEncodedFormEntity;
//import org.apache.http.client.methods.CloseableHttpResponse;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.client.methods.HttpRequestBase;
//import org.apache.http.client.protocol.HttpClientContext;
//import org.apache.http.config.Registry;
//import org.apache.http.config.RegistryBuilder;
//import org.apache.http.conn.ConnectTimeoutException;
//import org.apache.http.conn.routing.HttpRoute;
//import org.apache.http.conn.socket.ConnectionSocketFactory;
//import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
//import org.apache.http.conn.socket.PlainConnectionSocketFactory;
//import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
//import org.apache.http.message.BasicNameValuePair;
//import org.apache.http.protocol.HttpContext;
//import org.apache.logging.log4j.core.util.IOUtils;
//
//import javax.net.ssl.SSLException;
//import javax.net.ssl.SSLHandshakeException;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InterruptedIOException;
//import java.io.UnsupportedEncodingException;
//import java.net.UnknownHostException;
//import java.util.*;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//
//@Log4j2
//public class HttpConnectionPoolUtil {
//    private static final int CONNECT_TIMEOUT = Config.getHttpConnectTimeout();// �������ӽ����ĳ�ʱʱ��Ϊ10s
//    private static final int SOCKET_TIMEOUT = Config.getHttpSocketTimeout();
//    private static final int MAX_CONN = Config.getHttpMaxPoolSize(); // ���������
//    private static final int Max_PRE_ROUTE = Config.getHttpMaxPoolSize();
//    private static final int MAX_ROUTE = Config.getHttpMaxPoolSize();
//    private static CloseableHttpClient httpClient; // ��������Ŀͻ��˵���
//    private static PoolingHttpClientConnectionManager manager; //���ӳع�����
//    private static ScheduledExecutorService monitorExecutor;
//
//    private final static Object syncLock = new Object(); // �൱���߳���,�����̰߳�ȫ
//
//    /**
//     * ��http������л�������
//     * @param httpRequestBase http����
//     */
//    private static void setRequestConfig(HttpRequestBase httpRequestBase){
//        RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(CONNECT_TIMEOUT)
//                .setConnectTimeout(CONNECT_TIMEOUT)
//                .setSocketTimeout(SOCKET_TIMEOUT).build();
//
//        httpRequestBase.setConfig(requestConfig);
//    }
//
//    public static CloseableHttpClient getHttpClient(String url){
//        String hostName = url.split("/")[2];
//        System.out.println(hostName);
//        int port = 80;
//        if (hostName.contains(":")){
//            String[] args = hostName.split(":");
//            hostName = args[0];
//            port = Integer.parseInt(args[1]);
//        }
//
//        if (httpClient == null){
//            //���߳��¶���߳�ͬʱ����getHttpClient���׵����ظ�����httpClient���������,���Լ�����ͬ����
//            synchronized (syncLock){
//                if (httpClient == null){
//                    httpClient = createHttpClient(hostName, port);
//                    //��������߳�,���쳣�Ϳ����߳̽��йر�
//                    monitorExecutor = Executors.newScheduledThreadPool(1);
//                    monitorExecutor.scheduleAtFixedRate(new TimerTask() {
//                        @Override
//                        public void run() {
//                            //�ر��쳣����
//                            manager.closeExpiredConnections();
//                            //�ر�5s���е�����
//                            manager.closeIdleConnections(Config.getHttpIdelTimeout(), TimeUnit.MILLISECONDS);
//                            log.info("close expired and idle for over 5s connection");
//                        }
//                    }, Config.getHttpMonitorInterval(), Config.getHttpMonitorInterval(), TimeUnit.MILLISECONDS);
//                }
//            }
//        }
//        return httpClient;
//    }
//
//    /**
//     * ����host��port����httpclientʵ��
//     * @param host Ҫ���ʵ�����
//     * @param port Ҫ���ʵĶ˿�
//     * @return
//     */
//    public static CloseableHttpClient createHttpClient(String host, int port){
//        ConnectionSocketFactory plainSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
//        LayeredConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactory.getSocketFactory();
//        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create().register("http", plainSocketFactory)
//                .register("https", sslSocketFactory).build();
//
//        manager = new PoolingHttpClientConnectionManager(registry);
//        //�������Ӳ���
//        manager.setMaxTotal(MAX_CONN); // ���������
//        manager.setDefaultMaxPerRoute(Max_PRE_ROUTE); // ·�����������
//
//        HttpHost httpHost = new HttpHost(host, port);
//        manager.setMaxPerRoute(new HttpRoute(httpHost), MAX_ROUTE);
//
//        //����ʧ��ʱ,������������
//        HttpRequestRetryHandler handler = new HttpRequestRetryHandler() {
//            @Override
//            public boolean retryRequest(IOException e, int i, HttpContext httpContext) {
//                if (i > 3){
//                    //���Գ���3��,��������
//                    log.error("retry has more than 3 time, give up request");
//                    return false;
//                }
//                if (e instanceof NoHttpResponseException){
//                    //������û����Ӧ,�����Ƿ������Ͽ�������,Ӧ������
//                    log.error("receive no response from server, retry");
//                    return true;
//                }
//                if (e instanceof SSLHandshakeException){
//                    // SSL�����쳣
//                    log.error("SSL hand shake exception");
//                    return false;
//                }
//                if (e instanceof InterruptedIOException){
//                    //��ʱ
//                    log.error("InterruptedIOException");
//                    return false;
//                }
//                if (e instanceof UnknownHostException){
//                    // ���������ɴ�
//                    log.error("server host unknown");
//                    return false;
//                }
//                if (e instanceof ConnectTimeoutException){
//                    // ���ӳ�ʱ
//                    log.error("Connection Time out");
//                    return false;
//                }
//                if (e instanceof SSLException){
//                    log.error("SSLException");
//                    return false;
//                }
//
//                HttpClientContext context = HttpClientContext.adapt(httpContext);
//                HttpRequest request = context.getRequest();
//                if (!(request instanceof HttpEntityEnclosingRequest)){
//                    //��������ǹر����ӵ�����
//                    return true;
//                }
//                return false;
//            }
//        };
//
//        CloseableHttpClient client = HttpClients.custom().setConnectionManager(manager).setRetryHandler(handler).build();
//        return client;
//    }
//
//    /**
//     * ����post����Ĳ���
//     * @param httpPost
//     * @param params
//     */
//    private static void setPostParams(HttpPost httpPost, Map<String, String> params){
//        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
//        Set<String> keys = params.keySet();
//        for (String key: keys){
//            nvps.add(new BasicNameValuePair(key, params.get(key)));
//        }
//        try {
//            httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static JsonObject post(String url, Map<String, String> params){
//        HttpPost httpPost = new HttpPost(url);
//        setRequestConfig(httpPost);
//        setPostParams(httpPost, params);
//        CloseableHttpResponse response = null;
//        InputStream in = null;
//        JsonObject object = null;
//        try {
//            response = getHttpClient(url).execute(httpPost, HttpClientContext.create());
//            HttpEntity entity = response.getEntity();
//            if (entity != null) {
//                in = entity.getContent();
//                String result = IOUtils.toString(in, "utf-8");
//                JSONObject gson = new JSONObject();
//                object = JSONObject.parseObject(result);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            try{
//                if (in != null) in.close();
//                if (response != null) response.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        return object;
//    }
//
//    /**
//     * �ر����ӳ�
//     */
//    public static void closeConnectionPool(){
//        try {
//            httpClient.close();
//            manager.close();
//            monitorExecutor.shutdown();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}
