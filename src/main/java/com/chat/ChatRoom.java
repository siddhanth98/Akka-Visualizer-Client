package com.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import vis.Message;
import vis.MyVisualizerClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoom {
    interface RoomCommand extends Message {}

    /**
     * This represents the message a client sends to join the chat room
     * with the client's screen name and the client's actor ref
     */
    public static final class GetSession implements RoomCommand {
        public final long key;
        public final String screenName;
        public final ActorRef<SessionEvent> replyTo;

        public GetSession(long key, String screenName, ActorRef<SessionEvent> replyTo) {
            this.key = key;
            this.screenName = screenName;
            this.replyTo = replyTo;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    /**
     * This message is sent by a session actor to the chat room
     * to broadcast the message posted by session's client
     */
    public static final class PublishSessionMessage implements RoomCommand {
        public final long key;
        public final String screenName;
        public final String message;

        public PublishSessionMessage(long key, String screenName, String message) {
            this.key = key;
            this.screenName = screenName;
            this.message = message;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    interface SessionEvent extends Message {}

    /**
     * This represents a message that a client gets when it has
     * successfully joined the chat room
     */
    public static final class SessionGranted implements SessionEvent {
        public final long key;
        public final ActorRef<PostMessage> handle;

        public SessionGranted(long key, ActorRef<PostMessage> handle) {
            this.key = key;
            this.handle = handle;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    /**
     * This represents a message that a client gets when it
     * has been denied access to the chat room
     */
    public static final class SessionDenied implements SessionEvent {
        public final long key;
        public final String reason;

        public SessionDenied(long key, String reason) {
            this.key = key;
            this.reason = reason;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    /**
     * This message is received by the client when a chat message has been
     * posted in the chat room
     */
    public static final class MessagePosted implements SessionEvent {
        public final long key;
        public final String screenName;
        public final String message;

        public MessagePosted(long key, String screenName, String message) {
            this.key = key;
            this.screenName = screenName;
            this.message = message;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    interface SessionCommand extends Message {}

    /**
     * This message is sent by a client to its associated session actor
     * whenever the client needs to post a message in the chat room
     */
    public static final class PostMessage implements SessionCommand {
        public final long key;
        public final String message;

        public PostMessage(long key, String message) {
            this.key = key;
            this.message = message;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    /**
     * This message is sent by the chat room to a session actor
     * to notify the corresponding client of a posted message
     */
    public static final class NotifyClient implements SessionCommand {
        public final long key;
        public final MessagePosted message;

        public NotifyClient(long key, MessagePosted message) {
            this.key = key;
            this.message = message;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    /**
     * This message will be periodically sent by the chat room to itself
     * to sync it's own state with the visualizer
     */
    public static final class GetState implements RoomCommand {
        public final long key;

        public GetState(long key) {
            this.key = key;
        }

        public long getSenderKey() {
            return this.key;
        }
    }

    private final ActorContext<RoomCommand> context;
    private final MyVisualizerClient vis;
    private final long key;
    private final MyVisualizerClient.MessageWrapper wrapper;

    public static Behavior<RoomCommand> create(MyVisualizerClient vis) {
        return Behaviors.setup(context ->
            Behaviors.withTimers(timer -> {
                long key = vis.submit(context.getSelf().path().name());
                timer.startTimerWithFixedDelay(new GetState(key), Duration.ofMillis(1000));
                return new ChatRoom(vis, context, key).chatRoom(new ArrayList<>());
            }));
    }

    public ChatRoom(MyVisualizerClient vis, ActorContext<RoomCommand> context, long key) {
        this.vis = vis;
        this.context = context;
        this.wrapper = vis.new MessageWrapper();
        this.key = key;
        context.getLog().info(String.format("%s created", context.getSelf().path().name()));
    }

    public Behavior<RoomCommand> chatRoom(List<ActorRef<SessionCommand>> sessions) {
        return Behaviors.receive(RoomCommand.class)
                .onMessage(GetSession.class, m -> onGetSession(sessions, m))
                .onMessage(PublishSessionMessage.class, m -> onPublishSessionMessage(sessions, m))
                .onMessage(GetState.class, m -> syncState(sessions, m))
                .onSignal(Terminated.class, sig -> terminate())
                .build();
    }

    private Behavior<RoomCommand> onGetSession(List<ActorRef<SessionCommand>> sessions, GetSession m) throws UnsupportedEncodingException {
        this.wrapper.notify(context.getSelf().path().name(), m);
        ActorRef<SessionEvent> client = m.replyTo;
        ActorRef<SessionCommand> session = context.spawn(
                Session.create(vis, context.getSelf(), m.screenName, client),
                URLEncoder.encode(m.screenName, StandardCharsets.UTF_8.name())
        );
        client.tell(new SessionGranted(this.key, session.narrow()));

        List<ActorRef<SessionCommand>> newSessions = new ArrayList<>(sessions);
        newSessions.add(session);
        return chatRoom(newSessions);
    }

    private Behavior<RoomCommand> onPublishSessionMessage(List<ActorRef<SessionCommand>> sessions, PublishSessionMessage m) {
        NotifyClient notification = new NotifyClient(this.key, new MessagePosted(this.key, m.screenName, m.message));
        sessions.forEach(session -> {
            session.tell(notification);
        });
        return Behaviors.same();
    }

    public Behavior<RoomCommand> syncState(List<ActorRef<SessionCommand>> sessions, GetState m) {
        wrapper.notify(context.getSelf().path().name(), m);

        Map<String, Object> state = new HashMap<>();
        state.put("name", context.getSelf().path().name());
        state.put("sessions", sessions);
        vis.setState(state);
        return Behaviors.same();
    }

    public Behavior<RoomCommand> terminate() {
        vis.destroy(context.getSelf().path().name());
        return Behaviors.stopped();
    }

    static class Session {
        private final long key;
        private final MyVisualizerClient vis;
        private final MyVisualizerClient.MessageWrapper wrapper;
        private final ActorContext<SessionCommand> context;

        public static final class GetState implements SessionCommand {
            public long key;

            public GetState(long key) {
                this.key = key;
            }

            public long getSenderKey() {
                return this.key;
            }
        }

        static Behavior<SessionCommand> create(MyVisualizerClient vis, ActorRef<RoomCommand> room, String screenName,
                                               ActorRef<SessionEvent> client) {
            return Behaviors.setup(context -> new Session(context, vis, client).behavior(room, screenName, client));
        }

        public Session(ActorContext<SessionCommand> context, MyVisualizerClient vis,
                       ActorRef<SessionEvent> client) {
            this.context = context;
            this.vis = vis;
            this.key = vis.submit(context.getSelf().path().name());
            this.wrapper = vis.new MessageWrapper();
            context.getLog().info(String.format("%s created for %s",
                    context.getSelf().path().name(), client.path().name()));
        }

        public Behavior<SessionCommand> behavior(ActorRef<RoomCommand> room, String screenName, ActorRef<SessionEvent> client) {
            return Behaviors.receive(SessionCommand.class)
                    .onMessage(PostMessage.class, m -> onPostMessage(room, screenName, m))
                    .onMessage(NotifyClient.class, m -> onNotifyClient(client, m))
                    .onMessage(GetState.class, m -> syncState(client, screenName, m))
                    .onSignal(Terminated.class, sig -> terminate())
                    .build();
        }

        private Behavior<SessionCommand> onPostMessage(ActorRef<RoomCommand> room, String screenName, PostMessage message) {
            wrapper.notify(context.getSelf().path().name(), message); /* Notify receipt */

            room.tell(new PublishSessionMessage(this.key, screenName, message.message));
            return Behaviors.same();
        }

        private Behavior<SessionCommand> onNotifyClient(ActorRef<SessionEvent> client, NotifyClient message) {
            wrapper.notify(context.getSelf().path().name(), message); /* Notify receipt */

            client.tell(message.message);
            return Behaviors.same();
        }

        private Behavior<SessionCommand> syncState(ActorRef<SessionEvent> client, String screenName, GetState m) {
            wrapper.notify(context.getSelf().path().name(), m);
            Map<String, Object> state = new HashMap<>();
            state.put("name", context.getSelf().path().name());
            state.put("client", client.path().name());
            state.put("screenName", screenName);
            vis.setState(state);
            return Behaviors.same();
        }

        private Behavior<SessionCommand> terminate() {
            vis.destroy(context.getSelf().path().name());
            return Behaviors.stopped();
        }
    }
}
