import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class psvm {

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        final SelectionKey register = serverSocketChannel.register(selector, 16);

        serverSocketChannel.bind(new InetSocketAddress(2333));
        //只要一bind  底层c语言实现了一套完整 的 链接创建 和数据读取
        // java的api只是 从 c语言的进程中去取
        // 是为了 避免 如果 先绑定端口 那么jvm 底层 就直接开始 监听端口了
        //  因为 先  bind 端口 的话   后面 还有 注册 等一堆 方法 要走 这样 jvm底层 有可能在这个时间内  淤积 很多的 链接 【在bind上端口那一刻  底层就会开始监听端口和事件了】
        // 如果 当你 bind 后一大堆方法要走 那么  你真正取拿 链接 拿数据 可能 跟 链接真实创建 【底层c语言实现】
        //  会阁很长一段时间  所以先 注册 在bind
        // 之所以  事件 =0 是为了避免 空轮询 当 通道没有bind 端口的时候 轮询 是无用的  =0 jvm底层 不会做任何处理 只要不是那4个

        //当bind 端口的时候 一切都开始了
        Thread.sleep(10000);
        while (selector.select()>0){//其实 这个动作 只是 从 java 本地方法栈 中c语言的 进程中 去取  其实这个动作只是取拿
            System.out.println("a");
            break;
        }



    }
}
