import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;
import server.UnPooled;

public class Asss {


    public static void main(String[] args) {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(498);
        buffer.writeInt(0x00);
        buffer.writeInt(0xFF);
        String s = ByteBufUtil.hexDump(buffer);
        System.out.println(s);
    }
}
