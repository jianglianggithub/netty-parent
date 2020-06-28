package server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.http.websocketx.client.WebSocketClient;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.nio.charset.Charset;

public class WebSocketServer {


    public static void main(String[] args) throws Exception {




        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new LoggingHandler(LogLevel.TRACE))
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                            System.out.println("客户端消息" + msg.toString(Charset.defaultCharset()));
                                        }
                                    })
                                    .addLast(new LoggingHandler(LogLevel.TRACE))
                                    // HttpRequestDecoder和HttpResponseEncoder的一个组合，针对http协议进行编解码
                                    .addLast(new HttpServerCodec())
                                    // 分块向客户端写数据，防止发送大文件时导致内存溢出， channel.write(new ChunkedFile(new File("bigFile.mkv")))
                                    .addLast(new ChunkedWriteHandler())
                                    // 将HttpMessage和HttpContents聚合到一个完成的 FullHttpRequest或FullHttpResponse中,具体是FullHttpRequest对象还是FullHttpResponse对象取决于是请求还是响应
                                    // 需要放到HttpServerCodec这个处理器后面
                                    .addLast(new HttpObjectAggregator(10240))
                                    // webSocket 数据压缩扩展，当添加这个的时候WebSocketServerProtocolHandler的第三个参数需要设置成true
//                                    .addLast(new WebSocketServerCompressionHandler())
                                    // 服务器端向外暴露的 web socket 端点，当客户端传递比较大的对象时，maxFrameSize参数的值需要调大
                                    .addLast(new WebSocketServerProtocolHandler("/ws"))
                                    // 自定义处理器 - 处理 web socket 文本消息
//                                    .addLast(new TextWebSocketHandler())
                                    // 自定义处理器 - 处理 web socket 二进制消息

                                    .addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
                                            NioSocketChannel channel = (NioSocketChannel) ctx.channel();
                                            channel.parent().close();
                                            System.out.println(msg.content());
                                        }

                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                                                WebSocketServerProtocolHandler.HandshakeComplete  request=(WebSocketServerProtocolHandler.HandshakeComplete) evt;
                                                System.out.println(request.requestUri());
                                            }
                                            super.userEventTriggered(ctx, evt);
                                        }
                                    });

                        }
                    });
            ChannelFuture channelFuture = bootstrap.bind(2222).sync();

            channelFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }

}
