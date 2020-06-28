package server;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

/**
 * 在netty中  attr 是每一个 NioSocketChannle 或者NioServerSocketChannel 共享的  调用 ctx的 attr最终还是会加入到 所属的  Channle中的 attr中
 *
 * Channle  引用 PipeLine   反之    每一个ctx 都引用一个 handle 和对应的PipeLine
 *PipeLine 会维护一个 双向链表 在其中 Head ---ctx- ctx - Tail
 *
 *   在管道种 有 生命周期种的 各种  fre XXXXX 方法  然后调用 AbstractContext .xx方法(Head) 开启轮询 所有 处理器的钩子方法
 *
 * 4.0 和4.1 之后 attr 属性作用域是不同的 4.0 会给每一个 ctx 创建一个 结合 保留 一个attr 集合 节点=属性 下面的每一个attrbute 对应这个属性值
 *   4.1之后 作用域是  每一个channel  下的所有  ctx 共享一个  attr 集合  同名的属性可以有多个 因为 是 数组加链表的结构
 *
 * attr 的具体实现 是当要存储一个 attr的时候会 通过算法得到 attr对应的在 属性集合中的 索引位置，然后这个索引位置 只属于这个 属性
 * 然后 这个索引位 下的 单项链表 所有节点 逗是这个属性重复的值
 *
 *
 *   EventLoop 是线程安全的  因为 不管 Boss线程 有多少个 哪怕是多个 bos线程 在同时处理Accpet 然后 通过轮询  到对应的 NioEventLoop有多个 socketChannel
 *   也 会 放到  NioEventLoop种的 任务队列中 执行。【关键就在于他会判断 当前是不是属于 对应的EventLoop中的Tread 不是 就加入到队列中串行执行】
 *
 *
 *
 *                                   ctx.writeAndFlush();   这种 会在当前 ctx  直接 回到headContext
 *                                 ctx.channel().writeAndFlush()  而这个 方法 会从 TailContext  轮询所有 outBoundContext 回到 Head
 *
 *
 *   wirte指针 是下一次写的索引位，那么 wirte指针-1 等于 可读最大索引那么 wirte指针=limit 最大可读长度 减去已读  【已读索引 +1】=可读索引=能读的最大长度  那么这个长度-1就是最大可读 也就是wirte-read 就是 抛弃后的写指针
 *
 *
 *    为什么使用 堆内 的字节缓冲区 在真正进行IO 操作的时候 必须 需要 把内容 转移到  直接内存， 因为  io操作是比较耗时的 有可能在这个时候发生了gc操作，但是io操作 必须这个对象是静止的 所以 先考培到 堆外 避免gc移动，后再去 io写入通道
 *
 *    只有 创建在 堆上内存的缓冲区 才可以 获取 缓冲区的 每个字节 也就是 buffer.array()
 *
 *
 *      在bettt中 大多数情况下 逗是使用的 netty来管理的堆外内存 那么把字节缓冲区  放到 netty管理的 堆外内存 那么
 *      缓冲区的 内存释放 需要自己手动 来释放，否则会产生 内存泄漏
 *
 *
 *
 *      writeAnfFush 是 分为2步， wite  在fush 会走 2编  从当前节点 到head 然后 释放bytebuf 所占用的内存
 *
 *
 *
 *      ByteTomessage 和 MessageToMessage 的最大区别 就是 后者可以指定 编解码消息的 类型 前者 默认是  object
 *
 *    在netty中 只有 非池化  非直接缓冲区的 才能 用array() 方法
 *    */

public class Server {

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
                        ch.pipeline().addLast(new NettyInitializerHandle());
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
