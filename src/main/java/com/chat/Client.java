package com.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import vis.MyVisualizerClient;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Client {

    public static final class GetState implements ChatRoom.SessionEvent {
        public final long key;
        public GetState(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    public static final class PostSomething implements ChatRoom.SessionEvent {
        public final long key;
        public PostSomething(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    private final ActorContext<ChatRoom.SessionEvent> context;
    private final MyVisualizerClient vis;
    private final long key;
    private final String nodeType;
    private final MyVisualizerClient.MessageWrapper wrapper;

    public static Behavior<ChatRoom.SessionEvent> create(MyVisualizerClient vis, String nodeType) {
        Config config = ConfigFactory.parseFile(new File("src/main/resources/input.conf"));
        int selfDelay = config.getInt("conf.client-self-delay-ms");
        int stateSyncDelay = config.getInt("conf.client-state-sync-delay-ms");

        return Behaviors.setup(context ->
                Behaviors.withTimers(timer -> {
                    long key = vis.submit(context.getSelf().path().name());
                    timer.startTimerWithFixedDelay(new PostSomething(key), Duration.ofMillis(selfDelay));
                    timer.startTimerWithFixedDelay(new GetState(key), Duration.ofMillis(stateSyncDelay));
                    return new Client(context, vis, key, nodeType).behavior(null);
                })
        );
    }

    private Client(ActorContext<ChatRoom.SessionEvent> context, MyVisualizerClient vis, long key, String nodeType) {
        this.context = context;
        this.vis = vis;
        this.key = key;
        this.nodeType = nodeType;
        this.wrapper = vis.new MessageWrapper();
        context.getLog().info(String.format("%s created", context.getSelf().path().name()));
    }

    private Behavior<ChatRoom.SessionEvent> behavior(ActorRef<ChatRoom.PostMessage> handle) {
        return Behaviors.receive(ChatRoom.SessionEvent.class)
                .onMessage(ChatRoom.SessionGranted.class, this::onSessionGranted)
                .onMessage(ChatRoom.SessionDenied.class, this::onSessionDenied)
                .onMessage(ChatRoom.MessagePosted.class, this::onMessagePosted)
                .onMessage(GetState.class, this::syncState)
                .onMessage(PostSomething.class, m -> this.postSomething(m, handle))
                .build();
    }

    private Behavior<ChatRoom.SessionEvent> postSomething(PostSomething m, ActorRef<ChatRoom.PostMessage> handle) {
        String name = context.getSelf().path().name();
        wrapper.notify(name, m);

        if (handle != null) {
            int random = (int)(Math.random()*1000);
            context.getLog().info(String.format("%s: posting message - %d", name, random));
            handle.tell(new ChatRoom.PostMessage(this.key, String.valueOf(random)));
        }
        return Behaviors.same();
    }

    private Behavior<ChatRoom.SessionEvent> onSessionGranted(ChatRoom.SessionGranted m) {
        context.getLog().info(String.format("%s: session granted for me", context.getSelf().path().name()));
        wrapper.notify(context.getSelf().path().name(), m);

        m.handle.tell(new ChatRoom.PostMessage(this.key, String.format("Hello from %s%n", context.getSelf().path().name())));
        return this.behavior(m.handle);
    }

    private Behavior<ChatRoom.SessionEvent> onSessionDenied(ChatRoom.SessionDenied m) {
        wrapper.notify(context.getSelf().path().name(), m);
        context.getLog().info("Session denied for me");
        vis.destroy(context.getSelf().path().name());
        return Behaviors.stopped();
    }

    private Behavior<ChatRoom.SessionEvent> onMessagePosted(ChatRoom.MessagePosted m) {
        wrapper.notify(context.getSelf().path().name(), m);

        context.getLog().info(String.format("%s: %s posted message - %s%n",
                context.getSelf().path().name(), m.screenName, m.message));
        return Behaviors.same();
    }

    private Behavior<ChatRoom.SessionEvent> syncState(GetState m) {
        String name = context.getSelf().path().name();
        wrapper.notify(name, m);

        Map<String, Object> state = new HashMap<>();
        state.put("name", name);
        state.put("nodeType", nodeType);
        vis.setState(state);
        return Behaviors.same();
    }
}
