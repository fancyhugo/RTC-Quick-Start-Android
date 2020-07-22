package cn.rongcloud.quickstart;

import android.util.Log;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AppSSLUtils {
    public static SSLContext getSSLContext() {
        SSLContext sslContext = null;
        try {
            //使用X509TrustManager代替CertificateTrustManager跳过证书验证。
            TrustManager tm[] = {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            Log.d("checkClientTrusted", "authType:" + authType);
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            Log.d("checkServerTrusted", "authType:" + authType);
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tm, null);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        return sslContext;
    }

    public static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };


}