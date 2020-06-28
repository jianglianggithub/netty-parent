import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Sysn {
    static  int i=0;
    static Object o = new Object();
    public static void main(String[] args) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        String a = "aassssssssssssssssssssssssssssssssssssssssssssa";
        System.out.println(a.length());
        buffer.writeBytes(a.getBytes());

        buffer.readerIndex(10);

        buffer.discardReadBytes();
        System.out.println(buffer.refCnt());
        System.out.println(buffer.readableBytes());



    }

    public synchronized static void a(String run) throws Exception{




    }





}
