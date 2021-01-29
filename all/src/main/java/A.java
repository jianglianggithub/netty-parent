import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.nio.charset.Charset;

public class A {


    public static void main(String[] args) {

                 byte bytes = (byte) 0xff;

                 int result = bytes&0xff;
                System.out.println("无符号数: \t"+result);
                 System.out.println("2进制bit位: \t"+Integer.toBinaryString(result));
                 PooledByteBufAllocator.DEFAULT.buffer();
             }
//    public static void main(String[] args) {
//        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(10);
//        System.out.println(byteBuf.refCnt());
//        ByteBuf slice = byteBuf.slice();
//        int i = slice.refCnt();
//        System.out.println(i);
//        byteBuf.release();
//        System.out.println(slice.refCnt());
//        slice.readByte();
//    }
}
