package demo;

import io.netty.util.concurrent.FastThreadLocal;

public class A {


    public static void main(String[] args) {
        FastThreadLocal<Object> objectFastThreadLocal = new FastThreadLocal<>();
        objectFastThreadLocal.set("111");
        // 清空当前线程 的所有 本地变量缓存
        FastThreadLocal.removeAll();
    }
}
