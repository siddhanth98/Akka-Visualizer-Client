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
import java.util.HashMap;
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
        private final String label, to, event;
        private String from;

        public MessageEvent(@JsonProperty("event") String event,
                            @JsonProperty("label") String label,
                            @JsonProperty("receiver") String receiver,
                            @JsonProperty("time") long time) {
            super(time);
            this.event = event;
            this.label = label;
            this.to = receiver;
        }

        @JsonCreator
        public MessageEvent(@JsonProperty("event") String event,
                            @JsonProperty("label") String label,
                            @JsonProperty("from") String from,
                            @JsonProperty("to") String to,
                            @JsonProperty("time") long time) {
            this(event, label, to, time);
            this.from = from;
        }

        public String getEvent() {
            return event;
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

    public class MessageWrapper {
        Message message;
        String receiver;

        public MessageWrapper() {

        }

        public MessageWrapper(Message msg, String event, String receiver) {
            this.message = msg;
            switch(event) {
                case "send":
                    send(msg.getClass().getSimpleName(), getActorName(msg.getSenderKey()), receiver);
                    break;
                case "receive":
                    receive(msg.getClass().getSimpleName(), getActorName(msg.getSenderKey()), receiver);
            }
        }

        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }

        public Message getMessage() {
            return this.message;
        }

        public void setMessage(Message msg) {
            this.message = msg;
        }

        public void emit(String event) {
            switch(event) {
                case "send":
                    send(this.message.getClass().getSimpleName(), getActorName(this.message.getSenderKey()), this.receiver);
                    break;
                case "receive":
                    receive(this.message.getClass().getSimpleName(), getActorName(this.message.getSenderKey()), this.receiver);
                    break;
            }
        }
    }

    private final static Socket socket = IO.socket(URI.create("http://localhost:3001"));
    private long key;
    private final Map<Long, String> keyRef;

    public MyVisualizerClient() {
        socket.connect();
        socket.emit("setSocketId", "actorHandler");
        this.key = 0;
        this.keyRef = new HashMap<>();
    }

    public long submit(String actorName) {
        /* get unique key for this new actor and store it */
        long key = getUniqueKey();
        this.keyRef.put(key, actorName);
        System.out.printf("%d -> %s%n", key, actorName);

        long time = new Date().getTime();
        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("spawn", mapper.writeValueAsString(new ActorEvent(actorName, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
        return key;
    }

    public void send(String label, String from, String to) {
        long time = new Date().getTime();
        System.out.printf("%s from %s to %s, t = %d%n", label, from, to, time);
        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("send", mapper.writeValueAsString(new MessageEvent("send", label, from, to, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public void receive(String label, String sender, String receiver) {
        long time = new Date().getTime();
        System.out.printf("%s received by %s from %s, t = %d%n", label, sender, receiver, time);

        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("receive", mapper.writeValueAsString(new MessageEvent("receive", label, receiver, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
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

    public long getUniqueKey() {
        System.out.println("generating new key");
        long key = this.getKey();
        this.setKey(key+1);
        return key;
    }

    public String getActorName(long key) {
        if (this.keyRef.containsKey(key)) return this.keyRef.get(key);
        return "";
    }

    public long getKey() {
        return this.key;
    }

    public void setKey(long key) {
        this.key = key;
    }
}
