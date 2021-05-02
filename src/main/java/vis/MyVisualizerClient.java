package vis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.Map;

public class MyVisualizerClient {
    static class Event implements Serializable {
        private final long time;

        public Event(long time) {
            this.time = time;
        }

        public long getTime() {
            return this.time;
        }
    }

    static class ActorEvent extends Event implements Serializable {
        private final String name;

        @JsonCreator
        public ActorEvent(@JsonProperty("name") String name,
                          @JsonProperty("time") long time) {
            super(time);
            this.name = name;
        }

        public long getTime() {
            return super.getTime();
        }

        public String getName() {
            return this.name;
        }
    }

    static class MessageEvent extends Event implements Serializable {
        private final String label, from, to;

        @JsonCreator
        public MessageEvent(@JsonProperty("label") String label,
                            @JsonProperty("from") String from,
                            @JsonProperty("to") String to,
                            @JsonProperty("time") long time) {
            super(time);
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String getLabel() {
            return label;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public long getTime() {
            return super.getTime();
        }
    }

    private final static Socket socket = IO.socket(URI.create("http://localhost:3001"));

    public MyVisualizerClient() {
        socket.connect();
        socket.emit("setSocketId", "actorHandler");
    }

    public void submit(String actorName) {
        try {
            long time = new Date().getTime();
            ObjectMapper mapper = new ObjectMapper();

            socket.emit("constructNode", mapper.writeValueAsString(new ActorEvent(actorName, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public void send(String label, String from, String to) {
        try {
            long time = new Date().getTime();
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("constructEdge", mapper.writeValueAsString(new MessageEvent(label, from, to, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public void receive(String label, String receiver) {
        socket.emit("receive", label, receiver);
    }

    public void destroy(String actorName) {
        long time = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        try {
            socket.emit("destroyNode", mapper.writeValueAsString(new ActorEvent(actorName, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public void setState(Map<String, Object> state) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("setState", mapper.writeValueAsString(state));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }
}
