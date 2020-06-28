package mqtt_;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import server.NettyInitializerHandle;

public class MqttServer {


    public static void main(String[] args) throws Exception {

        final NioEventLoopGroup bos = new NioEventLoopGroup(1);
        final NioEventLoopGroup woker = new NioEventLoopGroup();
        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .channel(NioServerSocketChannel.class)
                .group(bos,woker)

                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new MqttDecoder());
                        ch.pipeline().addLast(MqttEncoder.INSTANCE);
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
//                                new MqttFixedHeader(MqttMessageType.CONNACK,)
//                                new MqttConnAckMessage()
//                                ctx.channel().writeAndFlush()
//                                new MqttPublishMessage()
                                System.out.println(msg);
                            }
                        });
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
