package com.test;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import vis.Message;
import vis.MyVisualizerClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Actor2 extends AbstractBehavior<Actor2.Command> {
    interface Command extends vis.Message {}

    public static class Increment implements Command {
        long key;
        public Increment(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class Display implements Actor2.Command {
        long key;
        public Display(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class State implements Actor2.Command {
        long key;
        public State(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class Greeting implements Actor2.Command {
        public final String message;
        long key;

        public Greeting(long key, String message) {
            this.message = message;
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    public static class PingActor implements Actor2.Command {
        long key;
        public final ActorRef<Actor2.Command> replyTo;

        public PingActor(long key, ActorRef<Actor2.Command> replyTo) {
            this.key = key;
            this.replyTo = replyTo;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    private final long key;
    private final MyVisualizerClient vis;
    private int count;
    private final String nodeType;
    private final MyVisualizerClient.MessageWrapper wrapper;

    public static Behavior<Actor2.Command> create(final int initialCount, final String nodeType, MyVisualizerClient vis) {
        return Behaviors.setup(context -> Behaviors.withTimers(timer -> {
            long key = vis.submit(context.getSelf().path().toString());
            timer.startTimerWithFixedDelay(new Actor2.State(key), Duration.ofMillis(3000));
            return new Actor2(key, context, initialCount, nodeType, vis);
        }));
    }

    private Actor2(long key, ActorContext<Actor2.Command> context, int count, String nodeType, MyVisualizerClient vis) {
        super(context);
        this.key = key;
        this.vis = vis;
        this.nodeType = nodeType;
        this.count = count;
        this.wrapper = this.vis.new MessageWrapper();
        context.getLog().info(String.format("%s created%n", context.getSelf().path().toString()));
    }

    public Receive<Actor2.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Actor2.Increment.class, this::onIncrement)
                .onMessage(Actor2.Display.class, this::display)
                .onMessage(Actor2.PingActor.class, this::pingActor)
                .onMessage(Actor2.State.class, this::syncState)
                .onMessage(Actor2.Greeting.class, this::getGreet)
                .onSignal(Terminated.class, sig -> {
                    vis.destroy(getContext().getSelf().path().toString()); /* tell vis to delete this actor node */
                    return Behaviors.stopped();
                }).build();
    }

    private Behavior<Actor2.Command> onIncrement(Increment msg) { /* message received, let vis know */
        notify(msg);
        this.count++;
        return this;
    }

    private Behavior<Actor2.Command> display(Display msg) {
        notify(msg);
        getContext().getLog().info(String.format("My current count - %d%n", this.count));
        return this;
    }

    private Behavior<Actor2.Command> pingActor(PingActor msg) {
        notify(msg);
        getContext().getLog().info(String.format("%s sending display message to %s",
                getContext().getSelf().path().toString(), msg.replyTo.path().toString()));
        notify(msg.replyTo.path().toString(), "send", new Display(this.key));
        msg.replyTo.tell((Display)this.wrapper.getMessage());
        return this;
    }

    private Behavior<Actor2.Command> getGreet(Greeting msg) {
        notify(msg);
        getContext().getLog().info(msg.message);
        return this;
    }

    public Behavior<Actor2.Command> syncState(State msg) {
        notify(msg);
        this.vis.setState(this.getState());
        return this;
    }

    public Map<String, Object> getState() {
        Map<String, Object> myState = new HashMap<>();
        myState.put("name", getContext().getSelf().path().toString());
        myState.put("count", this.count);
        myState.put("nodeType", this.nodeType);
        return myState;
    }

    public void notify(String receiver, String event, Message msg) {
        this.wrapper.setReceiver(receiver);
        this.wrapper.setMessage(msg);
        this.wrapper.emit(event);
    }

    public void notify(Message msg) {
        notify(getContext().getSelf().path().toString(), "receive", msg);
    }
}
