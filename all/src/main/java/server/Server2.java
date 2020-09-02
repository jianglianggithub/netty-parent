package server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ResourceLeakDetector;


public class Server2 {

    public static void main(String[] args) throws Exception {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer();

        byteBuf.release();
        ByteBuf byteBuf2 = PooledByteBufAllocator.DEFAULT.ioBuffer();

        final NioEventLoopGroup bos = new NioEventLoopGroup(1);
        final NioEventLoopGroup woker = new NioEventLoopGroup();
        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .channel(NioServerSocketChannel.class)
                .group(bos,woker)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.config().setReceiveBufferSize(1);
                        ch.pipeline()
                        .addLast(
                                new LengthFieldBasedFrameDecoder(65536,
                                        0,
                                        4,
                                        0,
                                        4),
                                new StringDecoder(),
                                new SimpleChannelInboundHandler<String>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                System.out.println(msg);
                            }
                        }
                                );

                    }
                });

        ChannelFuture channelFuture = serverBootstrap.bind(2333);
        channelFuture.sync();
        //  获取 对应得通道  由异步得等待 对方退出变为 同步等待
        channelFuture.channel().closeFuture().sync();


        bos.shutdownGracefully();
        woker.shutdownGracefully();
    }
}
