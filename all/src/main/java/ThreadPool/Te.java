package ThreadPool;

import io.netty.buffer.*;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Random;

public class Te {


    public static void main(String[] args) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(3);
        byteBuf.writeBytes("aaaa".getBytes());
    }
}
