package org.example.snapshot;

import lombok.extern.log4j.Log4j2;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class SnapshotProcessor {
    private static final ConcurrentHashMap<String, StockSnapshot> lastSnapshot = new ConcurrentHashMap<>();

    /** 异步
     * @param snapshot
     */
    public static void persistentData(StockSnapshot snapshot){
        if (!compareOld(snapshot)){
            return;
        }
        log.info(snapshot.toString());
    }

    /**
     * 和 lastSnapshot 里面的旧值比较，如果有更新则返回true，如果没有更新或者时间错小于0，就返回false
     * @param snapshot
     * @return
     */
    private static boolean compareOld(StockSnapshot snapshot) {
        if (!lastSnapshot.containsKey(snapshot.getCode())){
            lastSnapshot.put(snapshot.getCode(), snapshot);
            return true;
        }
        StockSnapshot old = lastSnapshot.get(snapshot.getCode());
        if (old.getTime().equals(snapshot.getTime())){
            return false;
        }
        if (!isCloser(parseDatetime(snapshot.getTime()), parseDatetime(old.getTime()))){
            return false;
        }
        lastSnapshot.put(snapshot.getCode(), snapshot);
        return true;
    }

    /**
     * 比较两个时间
     * @param last 大一点时间
     * @param prev 小一点的
     * @return
     */
    private static boolean isCloser(Date last, Date prev){
        return prev.compareTo(last) == 1;
    }

    private static Date parseDatetime(String datetime){
        DateFormat fmt =new SimpleDateFormat("yyyy/MM/dd HH:MM:SS");
        try {
            return fmt.parse(datetime);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


}
