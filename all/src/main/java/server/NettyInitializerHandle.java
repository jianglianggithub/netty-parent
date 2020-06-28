package server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;


import java.nio.charset.Charset;


public class NettyInitializerHandle extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new ChunkedWriteHandler())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebSocketServerProtocolHandler("/ws"))
                .addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
                        ByteBuf content = msg.content();
                        System.out.println(content.getClass());
                        System.out.println(content.toString(Charset.defaultCharset()));
                        TextWebSocketFrame aa= new TextWebSocketFrame("你吗四是");
                        ctx.channel().writeAndFlush(aa);
                    }
                });

    }


}
