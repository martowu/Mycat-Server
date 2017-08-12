package io.mycat.manager.response;

import io.mycat.MycatServer;
import io.mycat.backend.mysql.PacketUtil;
import io.mycat.config.Fields;
import io.mycat.manager.ManagerConnection;
import io.mycat.memory.MyCatMemory;
import io.mycat.memory.unsafe.Platform;
import io.mycat.memory.unsafe.utils.JavaUtils;
import io.mycat.net.mysql.EOFPacket;
import io.mycat.net.mysql.FieldPacket;
import io.mycat.net.mysql.ResultSetHeaderPacket;
import io.mycat.net.mysql.RowDataPacket;
import sun.rmi.runtime.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实现show@@directmemory功能
 *
 * @author zagnix
 * @version 1.0
 * @create 2016-09-21 17:35
 */

public class ShowDirectMemory {
    private static final int DETAILl_FIELD_COUNT = 3;
    private static final ResultSetHeaderPacket detailHeader = PacketUtil.getHeader(DETAILl_FIELD_COUNT);
    private static final FieldPacket[] detailFields = new FieldPacket[DETAILl_FIELD_COUNT];
    private static final EOFPacket detailEof = new EOFPacket();


    private static final int TOTAL_FIELD_COUNT = 5;
    private static final ResultSetHeaderPacket totalHeader = PacketUtil.getHeader(TOTAL_FIELD_COUNT);
    private static final FieldPacket[] totalFields = new FieldPacket[TOTAL_FIELD_COUNT];
    private static final EOFPacket totalEof = new EOFPacket();

    static {
        int i = 0;
        byte packetId = 0;
        detailHeader.packetId = ++packetId;

        detailFields[i] = PacketUtil.getField("THREAD_ID", Fields.FIELD_TYPE_VAR_STRING);
        detailFields[i++].packetId = ++packetId;

        detailFields[i] = PacketUtil.getField("MEM_USE_TYPE", Fields.FIELD_TYPE_VAR_STRING);
        detailFields[i++].packetId = ++packetId;

        detailFields[i] = PacketUtil.getField("  SIZE  ", Fields.FIELD_TYPE_VAR_STRING);
        detailFields[i++].packetId = ++packetId;
        detailEof.packetId = ++packetId;


        i = 0;
        packetId = 0;

        totalHeader.packetId = ++packetId;

        totalFields[i] = PacketUtil.getField("MDIRECT_MEMORY_MAXED", Fields.FIELD_TYPE_VAR_STRING);
        totalFields[i++].packetId = ++packetId;

        totalFields[i] = PacketUtil.getField("DIRECT_MEMORY_USED", Fields.FIELD_TYPE_VAR_STRING);
        totalFields[i++].packetId = ++packetId;

        totalFields[i] = PacketUtil.getField("DIRECT_MEMORY_AVAILABLE", Fields.FIELD_TYPE_VAR_STRING);
        totalFields[i++].packetId = ++packetId;

       totalFields[i] = PacketUtil.getField("SAFETY_FRACTION", Fields.FIELD_TYPE_VAR_STRING);
       totalFields[i++].packetId = ++packetId;

        totalFields[i] = PacketUtil.getField("DIRECT_MEMORY_RESERVED", Fields.FIELD_TYPE_VAR_STRING);
        totalFields[i++].packetId = ++packetId;
        totalEof.packetId = ++packetId;


    }


    public static void execute(ManagerConnection c,int showtype) {

        if(showtype == 1){
            showDirectMemoryTotal(c);
        }else if (showtype==2){
            showDirectMemoryDetail(c);
        }
    }


    public static void showDirectMemoryDetail(ManagerConnection c){

        ByteBuffer buffer = c.allocate();

        // write header
        buffer = detailHeader.write(buffer, c,true);

        // write fields
        for (FieldPacket field : detailFields) {
            buffer = field.write(buffer, c,true);
        }

        // write eof
        buffer = detailEof.write(buffer, c,true);

        // write rows
        byte packetId = detailEof.packetId;

        int useOffHeapForMerge = MycatServer.getInstance().getConfig().getSystem().getUseOffHeapForMerge();

        ConcurrentHashMap<Object,Long> networkbufferpool = MycatServer.getInstance().
                getBufferPool().getNetDirectMemoryUsage();

        try {

            if(useOffHeapForMerge == 1) {
                ConcurrentHashMap<Long, Long> concurrentHashMap = MycatServer.getInstance().
                        getMyCatMemory().
                        getResultMergeMemoryManager().getDirectMemorUsage();
                for (Long key : concurrentHashMap.keySet()) {


                    RowDataPacket row = new RowDataPacket(DETAILl_FIELD_COUNT);
                    Long value = concurrentHashMap.get(key);
                    row.add(String.valueOf(key).getBytes(c.getCharset()));
                    /**
                     * 该DIRECTMEMORY内存被结果集处理使用了
                     */
                    row.add("MergeMemoryPool".getBytes(c.getCharset()));
                    row.add(value > 0 ?
                            JavaUtils.bytesToString2(value).getBytes(c.getCharset()) : "0".getBytes(c.getCharset()));
                    row.packetId = ++packetId;
                    buffer = row.write(buffer, c, true);
                }
            }

            for (Object key:networkbufferpool.keySet()) {
                RowDataPacket row = new RowDataPacket(DETAILl_FIELD_COUNT);
                Long value = networkbufferpool.get(key);
                row.add(String.valueOf(key).getBytes(c.getCharset()));
                /**
                 * 该DIRECTMEMORY内存属于Buffer Pool管理的！
                 */
                row.add("NetWorkBufferPool".getBytes(c.getCharset()));
                row.add(value >0 ?
                        JavaUtils.bytesToString2(value).getBytes(c.getCharset()):"0".getBytes(c.getCharset()));

                row.packetId = ++packetId;
                buffer = row.write(buffer, c,true);
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // write last eof
        EOFPacket lastEof = new EOFPacket();
        lastEof.packetId = ++packetId;
        buffer = lastEof.write(buffer, c,true);

        // write buffer
        c.write(buffer);

    }


    public static void showDirectMemoryTotal(ManagerConnection c){

        ByteBuffer buffer = c.allocate();

        // write header
        buffer = totalHeader.write(buffer, c,true);

        // write fields
        for (FieldPacket field : totalFields) {
            buffer = field.write(buffer, c,true);
        }
        // write eof
        buffer = totalEof.write(buffer, c,true);
        // write rows
        byte packetId = totalEof.packetId;

        int useOffHeapForMerge = MycatServer.getInstance().getConfig().
                getSystem().getUseOffHeapForMerge();

        ConcurrentHashMap<Object,Long> networkbufferpool = MycatServer.getInstance().
                getBufferPool().getNetDirectMemoryUsage();

        RowDataPacket row = new RowDataPacket(TOTAL_FIELD_COUNT);
        long usedforMerge = 0;
        long usedforNetworkd = 0;

        try {

            /**
             * 通过-XX:MaxDirectMemorySize=2048m设置的值
             */
            row.add(JavaUtils.bytesToString2(Platform.getMaxDirectMemory()).getBytes(c.getCharset()));

            if(useOffHeapForMerge == 1) {

                /**
                 * 结果集合并时，总共消耗的DirectMemory内存
                 */
                ConcurrentHashMap<Long, Long> concurrentHashMap = MycatServer.getInstance().
                        getMyCatMemory().
                        getResultMergeMemoryManager().getDirectMemorUsage();
                for (Map.Entry<Long, Long> entry : concurrentHashMap.entrySet()) {
                    usedforMerge += entry.getValue();
                }
            }

            /**
             * 网络packet处理，在buffer pool 已经使用DirectMemory内存
             */
            for (Map.Entry<Object, Long> entry : networkbufferpool.entrySet()) {
                usedforNetworkd += entry.getValue();
            }

            row.add(JavaUtils.bytesToString2(usedforMerge+usedforNetworkd).getBytes(c.getCharset()));


            long totalAvailable = 0;

            if(useOffHeapForMerge == 1) {
                /**
                 * 设置使用off-heap内存处理结果集时，防止客户把MaxDirectMemorySize设置到物理内存的极限。
                 * Mycat能使用的DirectMemory是MaxDirectMemorySize*DIRECT_SAFETY_FRACTION大小，
                 * DIRECT_SAFETY_FRACTION为安全系数，为OS，Heap预留空间，避免因大结果集造成系统物理内存被耗尽！
                 */
                totalAvailable =  (long) (Platform.getMaxDirectMemory() * MyCatMemory.DIRECT_SAFETY_FRACTION);
            }else {
                totalAvailable = Platform.getMaxDirectMemory();
            }

            row.add(JavaUtils.bytesToString2(totalAvailable-usedforMerge-usedforNetworkd)
                    .getBytes(c.getCharset()));

            if(useOffHeapForMerge == 1) {
                /**
                 * 输出安全系统DIRECT_SAFETY_FRACTION
                 */
                row.add(("" + MyCatMemory.DIRECT_SAFETY_FRACTION)
                        .getBytes(c.getCharset()));
            }else {
                row.add(("1.0")
                        .getBytes(c.getCharset()));
            }


            long resevedForOs = 0;

            if(useOffHeapForMerge == 1){
                /**
                 * 预留OS系统部分内存！！！
                 */
                resevedForOs = (long) ((1-MyCatMemory.DIRECT_SAFETY_FRACTION)*
                        (Platform.getMaxDirectMemory()-
                                2*MycatServer.getInstance().getTotalNetWorkBufferSize()));
            }

            row.add(resevedForOs > 0 ?JavaUtils.bytesToString2(resevedForOs).getBytes(c.getCharset()):"0".getBytes(c.getCharset()));

            row.packetId = ++packetId;
            buffer = row.write(buffer, c,true);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // write last eof
        EOFPacket lastEof = new EOFPacket();
        lastEof.packetId = ++packetId;
        buffer = lastEof.write(buffer, c,true);

        // write buffer
        c.write(buffer);

    }




}
