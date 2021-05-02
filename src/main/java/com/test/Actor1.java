package com.test;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import vis.MyVisualizerClient;

import java.util.HashMap;
import java.util.Map;

public class Actor1 extends AbstractBehavior<Actor1.Command> {
    interface Command {}

    public static class Increment implements Command {}
    public static class Display implements Command {}
    public static class Greeting implements Command {
        public final String message;
        public Greeting(String message) {
            this.message = message;
        }
    }

    public static class PingActor implements Command {
        public final ActorRef<Command> replyTo;
        public PingActor(ActorRef<Command> replyTo) {
            this.replyTo = replyTo;
        }
    }

    private final MyVisualizerClient vis = new MyVisualizerClient();
    private int count;

    public static Behavior<Command> create(final int initialCount) {
        return Behaviors.setup(context -> new Actor1(context, initialCount));
    }

    private Actor1(ActorContext<Command> context, int count) {
        super(context);
        vis.submit(context.getSelf().path().toString()); /* submit actor ref to vis */
        context.getLog().info(String.format("%s created%n", context.getSelf().path().toString()));
        this.count = count;
        vis.setState(this.getState()); /* sync state with vis */
    }

    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Increment.class, m -> this.onIncrement())
                .onMessage(Display.class, m -> this.display())
                .onMessage(PingActor.class, m -> this.pingActor(m.replyTo))
                .onMessage(Greeting.class, m -> this.getGreet(m.message))
                .onSignal(Terminated.class, sig -> {
                    vis.destroy(getContext().getSelf().path().toString()); /* tell vis to delete this actor node */
                    return Behaviors.stopped();
                }).build();
    }

    private Behavior<Command> onIncrement() { /* message received, let vis know */
        this.count++;
        vis.setState(getState()); /* state changes here, so sync state with vis */
        return this;
    }

    private Behavior<Command> display() {
        getContext().getLog().info(String.format("My current count - %d%n", this.count));
        return this;
    }

    private Behavior<Command> pingActor(ActorRef<Command> replyTo) {
        getContext().getLog().info(String.format("%s sending display message to %s",
                getContext().getSelf().path().toString(), replyTo.path().toString()));
        replyTo.tell(new Actor1.Display());
        vis.send("display", getContext().getSelf().path().toString(), replyTo.path().toString()); /* notify vis about message being sent */
        return this;
    }

    private Behavior<Command> getGreet(String message) {
        getContext().getLog().info(message);
        return this;
    }

    public Map<String, Object> getState() {
        Map<String, Object> myState = new HashMap<>();
        myState.put("name", getContext().getSelf().path().toString());
        myState.put("count", this.count);
        return myState;
    }
}
