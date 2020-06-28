package server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerSocketEvent extends ChannelInboundHandlerAdapter {

    public static void main(String[] args) throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(1);
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread("thead0") {

            FastThreadLocal<Object> local=new FastThreadLocal<>();
            FastThreadLocal<Object> local1=new FastThreadLocal<>();
            FastThreadLocal<Object> local2=new FastThreadLocal<>();
            FastThreadLocal<Object> local3=new FastThreadLocal<>();
            FastThreadLocal<Object> local4=new FastThreadLocal<>();
            FastThreadLocal<Object> local5=new FastThreadLocal<>();
            FastThreadLocal<Object> local6=new FastThreadLocal<>();
            FastThreadLocal<Object> local7=new FastThreadLocal<>();
            FastThreadLocal<Object> local8=new FastThreadLocal<>();
            FastThreadLocal<Object> local9=new FastThreadLocal<>();
            FastThreadLocal<Object> local10=new FastThreadLocal<>();
            FastThreadLocal<Object> local11=new FastThreadLocal<>();
            FastThreadLocal<Object> local12=new FastThreadLocal<>();
            FastThreadLocal<Object> local13=new FastThreadLocal<>();
            FastThreadLocal<Object> local14=new FastThreadLocal<>();
            FastThreadLocal<Object> local15=new FastThreadLocal<>();
            FastThreadLocal<Object> local16=new FastThreadLocal<>();
            FastThreadLocal<Object> local17=new FastThreadLocal<>();
            FastThreadLocal<Object> local18=new FastThreadLocal<>();
            FastThreadLocal<Object> local19=new FastThreadLocal<>();
            FastThreadLocal<Object> local20=new FastThreadLocal<>();
            FastThreadLocal<Object> local21=new FastThreadLocal<>();
            FastThreadLocal<Object> local22=new FastThreadLocal<>();
            FastThreadLocal<Object> local23=new FastThreadLocal<>();
            FastThreadLocal<Object> local24=new FastThreadLocal<>();
            FastThreadLocal<Object> local25=new FastThreadLocal<>();
            FastThreadLocal<Object> local26=new FastThreadLocal<>();
            FastThreadLocal<Object> local27=new FastThreadLocal<>();
            FastThreadLocal<Object> local28=new FastThreadLocal<>();
            FastThreadLocal<Object> local29=new FastThreadLocal<>();
            FastThreadLocal<Object> local30=new FastThreadLocal<>();
            FastThreadLocal<Object> local31=new FastThreadLocal<>();


            @Override
            public void run() {
                local31.set("");
                Thread thread = Thread.currentThread();
                if (thread instanceof  FastThreadLocalThread) {
                    FastThreadLocalThread FastThreadLocalThread=(FastThreadLocalThread)thread;

                }
            }
        };
        fastThreadLocalThread.start();

//        countDownLatch.await();
//        FastThreadLocalThread fastThreadLocalThread1 = new FastThreadLocalThread("thead1") {
//
//            FastThreadLocal<Object> local2=new FastThreadLocal<>();
//
//            @Override
//            public void run() {
//                Object o = local2.get();
//                System.out.println(o);
//            }
//        };
//        fastThreadLocalThread1.start();
    }
    //构建链接组
    public static DefaultChannelGroup defaultChannelGroup=new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    List list=new ArrayList();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Added"+" 存活个数 "+defaultChannelGroup.size());
        list.add(ctx.channel());
        //当有链接加入
        System.out.println(defaultChannelGroup.hashCode());
        defaultChannelGroup.writeAndFlush("[ 服务器 推送 通知 ]").addListener(null);
        defaultChannelGroup.add(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        System.out.println(defaultChannelGroup.size()+"    "+list.size());
        defaultChannelGroup.forEach(channel ->
        {
            if (ctx.channel()==channel){
                channel.writeAndFlush("[ 这条消息来自自己发出 ]"+msg+"\n");
            }else {
                channel.writeAndFlush("[ 推送其他链接人消息 ]"+msg+"\n");
            }
        });

    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        defaultChannelGroup.writeAndFlush("[服务器推送下线通道]"+ctx.channel().remoteAddress()+"\n");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
