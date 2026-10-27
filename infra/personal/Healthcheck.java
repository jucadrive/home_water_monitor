import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Probes the protected JSON boundary without storing a login token. */
public class Healthcheck {
    public static void main(String[] args) throws Exception {
        String url = args.length == 0 ? "http://127.0.0.1:8080/api/cultivations" : args[0];
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3)).GET().build();
        var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 401 || !response.body().contains("AUTH_REQUIRED")) {
            System.exit(1);
        }
    }
}
