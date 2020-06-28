import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.Charset;

public class Web {

    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup woker = new NioEventLoopGroup();


        ServerBootstrap bootstrap=new ServerBootstrap()
                .group(boss,woker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpServerCodec(),
                                        new HttpObjectAggregator(65535),
                                        new SimpleChannelInboundHandler<FullHttpRequest>() {
                                            @Override
                                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {


                                            }


                                            @Override
                                            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                                                System.out.println(ctx.channel().isActive());
                                            }

                                            @Override
                                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                                System.out.println(ctx.channel().isActive());
                                            }

                                            @Override
                                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                                System.out.println(" active ");
                                            }

                                            @Override
                                            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                                                System.out.println("handle add");
                                            }
                                        });
                    }
                });

        ChannelFuture sync = bootstrap.bind(2222).sync();

        sync.channel().closeFuture().sync();
    }
}
