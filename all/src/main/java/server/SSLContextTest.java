package server;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

public class SSLContextTest {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        SSLContext ssLv3 = SSLContext.getInstance("SSLv3");
        System.out.println(ssLv3);
    }
}
