import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.nio.charset.Charset;

public class A {



    public static void main(String[] args) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(10);
        System.out.println(byteBuf.refCnt());
        ByteBuf slice = byteBuf.slice();
        int i = slice.refCnt();
        System.out.println(i);
        byteBuf.release();
        System.out.println(slice.refCnt());
        slice.readByte();
    }
}
