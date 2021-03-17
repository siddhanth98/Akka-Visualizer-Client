package vis;

import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyVisualizerClient {
    private final static Socket socket = IO.socket(URI.create("http://localhost:3001"));

    public MyVisualizerClient() {
        socket.connect();
        socket.emit("setSocketId", "actorHandler");
    }

    public static void main(String[] args) {
        try {
            socket.connect();
            Map<String, List<Integer>> m = new HashMap<>();
            m.put("a", List.of(1,2,3));
            m.put("b", List.of(4,5,6));
            socket.emit("hello", m, List.of(10, 11));
            socket.on("disconnect", (a) -> socket.disconnect());
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void submit(String actorName) {
        socket.emit("constructNode", actorName);
    }

    public void send(String label, String from, String to) {
        socket.emit("constructEdge", label, from, to);
    }

    public void destroy(String actorName) {
        socket.emit("destroyNode", actorName);
    }

    public void setState(Map<String, Object> state) {
        socket.emit("setState", state);
    }
}
