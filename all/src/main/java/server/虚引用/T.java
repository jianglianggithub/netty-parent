package server.虚引用;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;

/**
 *  虚引用和 弱引用其实是差不多的。。只是虚引用 get 始终返回null而已？
 */
public class T {
    public static void main(String[] args) throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        String s = new String("11");
        ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
        PhantomReference<Object> objectPhantomReference = new PhantomReference<>(s, referenceQueue);
        s = null;
        System.gc();
        Thread.sleep(1000);
        while (true) {
            Reference<?> poll = referenceQueue.poll();

            if (poll != null) {
                Field rereferent = Reference.class
                        .getDeclaredField("referent");
                rereferent.setAccessible(true);
                Object result = rereferent.get(poll);
                System.out.println(result);
                System.out.println(poll.get());
                break;
            }
        }
    }
}
