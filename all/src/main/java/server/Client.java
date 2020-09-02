package server;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.ResourceLeakDetector;

import java.net.InetSocketAddress;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) throws InterruptedException {
//        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
//        aaa();
//        System.gc();
//        Thread.sleep(5000);
//        PooledByteBufAllocator.DEFAULT.ioBuffer();
        final NioEventLoopGroup woker = new NioEventLoopGroup();
//        LengthFieldPrepender lengthFieldPrepender = new LengthFieldPrepender(4);
        final Bootstrap serverBootstrap = new Bootstrap();
        serverBootstrap
                .channel(NioSocketChannel.class)
                .group(woker)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                                new StringEncoder(),
                                new ChannelInboundHandlerAdapter(){
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                ctx.write("你好服务器");
                                System.out.println("发送成功");
                            }
                        });
                    }
                });

         ChannelFuture localhost = serverBootstrap.connect(new InetSocketAddress("localhost", 2333)).sync();


        //  获取 对应得通道  由异步得等待 对方退出变为 同步等待
        Channel channel = localhost.channel();

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            scanner.next();
            String content="abcsdfsdfwerwwwwwwwwwwwwwwwwwwwwwwwwwwww11wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww234324d";
            int length = content.getBytes().length;
            ByteBuf buffer = Unpooled.buffer(4 + length);
            buffer.writeInt(length);
            buffer.writeBytes(content.getBytes());
            channel.writeAndFlush(buffer);

        }


        woker.shutdownGracefully();
    }

    //内存泄漏 demo
    private static void aaa() {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer();
        System.out.println(byteBuf.getClass());
        byteBuf.writeBytes("aa".getBytes());
    }


}
