import java.net.HttpURLConnection;
import java.net.URI;

final class HealthProbe {
    public static void main(String[] args) throws Exception {
        var connection = (HttpURLConnection) URI.create("http://127.0.0.1:8080/readyz").toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) {
                System.exit(1);
            }
        } finally {
            connection.disconnect();
        }
    }
}
