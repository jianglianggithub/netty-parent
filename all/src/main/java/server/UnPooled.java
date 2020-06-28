package server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class UnPooled extends MessageToByteEncoder {

    public static void main(String[] args) {
        // Unpooled 非池化缓冲区
        //  有UnppoledHeapByteBuff  直接new byte[]
        //   UnpooledUnsafeHeapByteBuff  会使用unsafe创建 字节数组  会通过unsafe 去取每个字节 也是直接 缓冲区
        // 非池化的缓冲区 netty是 不会给每个缓冲区 分配单独的引用计数 的实现 而是 全部使用一个 助手静态类 来实现的

        //在netty中 线程工厂 最好使用 netty提供的 这样子 使用 FastThreadLocal会有优化
        DefaultEventExecutorGroup defaultEventExecutorGroup = new DefaultEventExecutorGroup(1,
                new ThreadFactory() {
                   AtomicInteger atomicInteger=new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {

                        return new Thread(r,"aa_"+atomicInteger.getAndIncrement());
                    }
                });

        defaultEventExecutorGroup.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getClass());
            }
        });

    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {

    }
}
