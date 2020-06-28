package ThreadPool;

public class Test1 {

    /**
     *                  1 1  11111 11111 11111 11111 11111 11111     = -1 << 29
     *                  1 1  10000 00000 00000 00000 00000 00000
     *                  1 1  01111 11111 11111 11111 11111 11111
     *                  00  10000  00000 00000 00000 00000 00000
     *
     *
     * @param args
     */

    public static void main(String[] args) {
        System.out.println(1<<29);
         //536870912  -1 << 29 =-536870912
    }
}
