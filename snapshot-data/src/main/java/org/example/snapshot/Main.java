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
                        //伪代码
                        long startTime=System.currentTimeMillis();   //获取开始时间
                        Task.taskItem();
                        long endTime=System.currentTimeMillis(); //获取结束时间
                        log.warn("Time： " + (endTime-startTime) + " ms");
                        // sleep(1*1000);
                    }

                }
            }).start();
        }

    }
}
