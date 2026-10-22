import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.*;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

/** Bench-only wire observer/publisher. Reuses the backend's pinned Paho 1.2.5.
 * Credentials come from process environment; stdout is a protocol consumed by Python. */
public class BenchMqtt {
    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    private static void requireAck(IMqttToken token) throws Exception {
        token.waitForCompletion(10000);
        if (token.getReasonCodes() != null) for (int code : token.getReasonCodes()) {
            if (code >= 128) throw new IllegalStateException("BROKER_REJECTED");
        }
    }
    public static void main(String[] args) {
        MqttAsyncClient client = null;
        int exit = 0;
        try {
            client = new MqttAsyncClient(System.getenv().getOrDefault("HWM_TEST_BROKER_URI", "tcp://127.0.0.1:18883"),
                System.getenv().getOrDefault("HWM_TEST_CLIENT_ID", "bench-" + UUID.randomUUID()), new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                public void disconnected(MqttDisconnectResponse response) { System.out.println("DISCONNECTED"); }
                public void mqttErrorOccurred(MqttException error) { System.out.println("MQTT_ERROR"); }
                public void deliveryComplete(IMqttToken token) {}
                public void connectComplete(boolean reconnect, String uri) {}
                public void authPacketArrived(int reason, MqttProperties properties) {}
                public void messageArrived(String topic, MqttMessage message) {
                    MqttProperties p = message.getProperties();
                    String content = p == null || p.getContentType() == null ? "" : p.getContentType();
                    System.out.println("MESSAGE\t" + b64(topic) + "\t" + message.getQos() + "\t" + message.isRetained()
                        + "\t" + (p != null && p.getPayloadFormat()) + "\t" + b64(content) + "\t"
                        + Base64.getEncoder().encodeToString(message.getPayload())
                        + (System.getenv("HWM_TEST_INCLUDE_EXPIRY") == null ? "" : "\t" + (p == null ? null : p.getMessageExpiryInterval())));
                }
            });
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            options.setSessionExpiryInterval(0L);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setAutomaticReconnect(false);
            options.setMaximumPacketSize(65536L);
            options.setUserName(System.getenv("HWM_TEST_MQTT_USERNAME"));
            options.setPassword(System.getenv("HWM_TEST_MQTT_PASSWORD").getBytes(StandardCharsets.UTF_8));
            requireAck(client.connect(options));
            if (args[0].equals("listen")) {
                MqttSubscription[] subscriptions = new MqttSubscription[args.length - 2];
                for (int i = 2; i < args.length; i++) {
                    subscriptions[i - 2] = new MqttSubscription(args[i], 1);
                    subscriptions[i - 2].setRetainAsPublished(true);
                    subscriptions[i - 2].setRetainHandling(2);
                }
                requireAck(client.subscribe(subscriptions));
                System.out.println("READY");
                Thread.sleep(Math.min(Integer.parseInt(args[1]), 600) * 1000L);
            } else if (args[0].equals("publish")) {
                MqttMessage message = new MqttMessage(Files.readAllBytes(Path.of(args[2])));
                message.setQos(Integer.parseInt(args[3]));
                message.setRetained(Boolean.parseBoolean(args[4]));
                MqttProperties p = new MqttProperties();
                if (!args[5].equals("missing")) p.setPayloadFormat(Boolean.parseBoolean(args[5]));
                if (!args[6].equals("missing")) p.setContentType(args[6]);
                if (args.length > 7) p.setMessageExpiryInterval(Long.parseLong(args[7]));
                message.setProperties(p);
                requireAck(client.publish(args[1], message));
                System.out.println("PUBLISHED");
            } else throw new IllegalArgumentException();
        } catch (Exception error) {
            System.out.println("BENCH_MQTT_FAILED category=" + error.getClass().getSimpleName());
            exit = 1;
        } finally {
            if (client != null) try {
                if (client.isConnected()) client.disconnect(1000).waitForCompletion(3000);
                client.close(true);
            } catch (Exception ignored) { exit = 1; }
        }
        System.exit(exit);
    }
}
