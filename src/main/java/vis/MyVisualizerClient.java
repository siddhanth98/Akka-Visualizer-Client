package vis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class intercepts all required actor system events
 * and emits socket events to the node JS server for each of them
 * @author Siddhanth Venkateshwaran
 */
public class MyVisualizerClient {

    /**
     * Represents a generic actor event with the timestamp and state
     * being serialized
     */
    static class Event implements Serializable {
        private final long time;
        private final Map<String, Object> state;

        public Event(long time) {
            this(time, new HashMap<>());
        }

        @JsonCreator
        public Event(@JsonProperty("time") long time,
                     @JsonProperty("state") Map<String, Object> state) {
            this.time = time;
            this.state = state;
        }

        public long getTime() {
            return this.time;
        }

        public Map<String, Object> getState() {
            return this.state;
        }
    }

    /**
     * This represents the spawn/destroy event of an actor entity
     */
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

    /**
     * This represents the message receipt event notified by one
     * registered actor entity
     */
    static class MessageEvent extends Event implements Serializable {
        private final String label, to, event;
        private final String from;

        @JsonCreator
        public MessageEvent(@JsonProperty("event") String event,
                            @JsonProperty("label") String label,
                            @JsonProperty("from") String from,
                            @JsonProperty("to") String to,
                            @JsonProperty("time") long time) {
            super(time);
            this.event = event;
            this.label = label;
            this.to = to;
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

    /**
     * This class is used to access the sender of a message using
     * the unique key of the actor entity which was inserted at the
     * time of creation.
     */
    public class MessageWrapper {
        Message message;
        String receiver;
        long timestamp = -1;

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
            this.timestamp =
                    receive(this.message.getClass().getSimpleName(), getActorName(this.message.getSenderKey()), this.receiver);
        }

        public void notify(String receiver, String event, Message msg) {
            this.setReceiver(receiver);
            this.setMessage(msg);
            this.emit(event);
        }

        public void notify(String receiver, Message m) {
            this.notify(receiver, "receive", m);
        }

        public long getTimestamp() {
            return this.timestamp;
        }
    }

    private final static Socket socket = IO.socket(URI.create("http://localhost:3001"));
    private final static Logger logger = LoggerFactory.getLogger(MyVisualizerClient.class);
    private long key;
    private final Map<Long, String> keyRef;
    private final Map<String, Long> invertedKeyRef;

    public MyVisualizerClient() {
        socket.connect();
        socket.emit("setSocketId", "actorHandler");
        this.key = 0;
        this.keyRef = new HashMap<>();
        this.invertedKeyRef = new HashMap<>();
    }

    /**
     * Registers an actor entity and emits a spawn event to the server
     * @param actorName Path name of the newly created actor entity
     * @return Newly created key for the actor entity
     */
    public long submit(String actorName) {
        /* get unique key for this new actor and store it */
        long key = getUniqueKey();
        this.keyRef.put(key, actorName);
        this.invertedKeyRef.put(actorName, key);

        logger.info(String.format("submitting %s(key=%d)", actorName, key));

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

    /**
     * This intercepts the receipt of a message by an actor and emits
     * a corresponding receive event to the server
     * @param label Name of the message sent
     * @param sender Sender actor name of the message
     * @param receiver Receiver actor name of the message
     * @return Timestamp of the event
     */
    public long receive(String label, String sender, String receiver) {
        long time = new Date().getTime();
        logger.info(String.format("%s sent %s to %s (t=%d)%n", sender, label, receiver, time));

        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("receive", mapper.writeValueAsString(new MessageEvent("receive", label, sender, receiver, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
        return time;
    }

    /**
     * This intercepts the termination event of an actor and
     * emits a destroy event to the server
     * @param actorName Path name of the actor which was terminated
     */
    public void destroy(String actorName) {
        long time = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        try {
            socket.emit("destroyNode", mapper.writeValueAsString(new ActorEvent(actorName, time)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
        this.keyRef.remove(this.invertedKeyRef.get(actorName));
        this.invertedKeyRef.remove(actorName);
        logger.info(String.format("Destroyed %s", actorName));
    }

    /**
     * This method is called by the relevant actor program to
     * sync the state of the actor entity with the visualizer
     * It emits an state update event to the server
     * @param state Map of property names to object values
     */
    public void setState(Map<String, Object> state) {
        long time = new Date().getTime();
        try {
            ObjectMapper mapper = new ObjectMapper();
            socket.emit("setState", mapper.writeValueAsString(new Event(time, state)));
        }
        catch(JsonProcessingException ex) {
            ex.printStackTrace();
        }
    }

    public long getUniqueKey() {
        long key = this.getKey();
        this.setKey(key+1);
        return key;
    }

    public String getActorName(long key) {
        if (this.keyRef.containsKey(key)) return this.keyRef.get(key);
        return "";
    }

    public Map<String, Long> getInvertedKeyRef() {
        return this.invertedKeyRef;
    }

    public long getKey() {
        return this.key;
    }

    public void setKey(long key) {
        this.key = key;
    }
}
