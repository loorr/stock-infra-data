package org.example.snapshot;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import static java.lang.Thread.sleep;


@Log4j2
public class Main {


    @SneakyThrows
    public static void main(String[] args) {
        for (int i = 0; i < 100 ; i++) {
            new Thread(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    while (true){
                        //α����
                        long startTime=System.currentTimeMillis();   //��ȡ��ʼʱ��
                        Task.taskItem();
                        long endTime=System.currentTimeMillis(); //��ȡ����ʱ��
                        log.warn("Time�� " + (endTime-startTime) + " ms");
                        // sleep(1*1000);
                    }

                }
            }).start();
        }

    }
}
