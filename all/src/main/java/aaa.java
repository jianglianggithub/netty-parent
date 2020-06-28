import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.Recycler;

import java.nio.charset.Charset;

public class aaa {
    public static void main(String[] args) {
        Recycler<User> recycler=new Recycler() {
            @Override
            protected Object newObject(Handle handle) {
                return new User(handle);
            }
        };
        User user = recycler.get();
        user.recycler();
        ByteBufAllocator byteBufAllocator = ByteBufAllocator.DEFAULT; //缓冲区分配器
//        ByteBuf byteBuf = byteBufAllocator.ioBuffer(32*1024*2);
//        ByteBuf byteBuf1 = byteBufAllocator.ioBuffer(32*1024*2);
//
//        ByteBuf byteBuf2 = byteBufAllocator.ioBuffer(32*1024*2);
//        ByteBuf byteBuf3 = byteBufAllocator.ioBuffer(32*1024*2);
        ByteBuf byteBuf4 = byteBufAllocator.ioBuffer(32*1024*2);
//        ByteBuf byteBuf5 = byteBufAllocator.ioBuffer(32*1024*2);
//        ByteBuf byteBuf6 = byteBufAllocator.ioBuffer(32*1024*2);
//        byteBuf1.release();
//        byteBuf.release();
//        byteBuf2.release();
//        byteBuf3.release();
//        byteBuf4.release();
//        byteBuf5.release();
//        byteBuf6.release();
        PooledByteBufAllocator.DEFAULT.buffer();

    }

    static  class User{
        Recycler.Handle handle;
        public User(Recycler.Handle handle){
            this.handle=handle;
        }
        public void recycler(){
            handle.recycle(this);
        }
    }
}
