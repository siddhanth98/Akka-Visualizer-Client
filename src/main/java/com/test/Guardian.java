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

public class Guardian extends AbstractBehavior<Guardian.Command> {
    private final long key;
    private ActorRef<Actor2.Command> actor1;
    private ActorRef<Actor2.Command> actor2;
    private int index = 3;
    private final MyVisualizerClient vis;
    private final MyVisualizerClient.MessageWrapper wrapper;

    interface Command extends Message {}

    public static class Spawn implements Command {
        public final long key;
        public Spawn(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class InitiateMessageTransfer implements Command {
        public final long key;
        public InitiateMessageTransfer(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class ScheduleMessage implements Command {
        public final long key;
        public ScheduleMessage(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }
    public static class Kill implements Command {
        public final long key;
        public Kill(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    public static Behavior<Command> create(MyVisualizerClient vis) {
        return Behaviors.setup(context -> {
            long key = vis.submit(context.getSelf().path().name());
            context.getSelf().tell(new Spawn(key));
            return Behaviors.withTimers(timer -> {
                timer.startTimerWithFixedDelay(new ScheduleMessage(key), Duration.ofMillis(1000));
                return new Guardian(key, context, vis);
            });
        });
    }

    private Guardian(long key, ActorContext<Command> context, MyVisualizerClient vis) {
        super(context);
        this.vis = vis;
        this.key = key;
        this.wrapper = vis.new MessageWrapper();
        context.getLog().info(String.format("%s created%n", context.getSelf().path().name()));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Spawn.class, this::handleSpawn)
                .onMessage(InitiateMessageTransfer.class, this::handleMessageTransfer)
                .onMessage(ScheduleMessage.class, this::handleSchedule)
                .onMessage(Kill.class, this::kill)
                .onSignal(Terminated.class, sig -> {
                    vis.destroy(getContext().getSelf().path().name()); /* tell vis to delete this actor node */
                    return Behaviors.stopped();
                }).build();
    }

    private Behavior<Command> handleSpawn(Spawn m) {
        actor1 = getContext().spawn(Actor2.create(10, "control-node", this.vis), "Actor-1");
        actor2 = getContext().spawn(Actor2.create(0, "data-node", this.vis), "Actor-2");
        return this;
    }

    private Behavior<Command> handleMessageTransfer(InitiateMessageTransfer m) {
        wrapper.notify(getContext().getSelf().path().name(), m);
        actor1.tell(new Actor2.Increment(this.key));
        actor2.tell(new Actor2.Increment(this.key));
        return this;
    }

    private Behavior<Command> handleSchedule(ScheduleMessage m) {
        wrapper.notify(getContext().getSelf().path().name(), m);
        int random = (int)(Math.random()*3);
        int messageToSend = (int)(Math.random()*2);

        if (random == 0) {
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor1.path().name()));
                actor1.tell(new Actor2.Increment(this.key));
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor1.path().name()));
                actor1.tell(new Actor2.Display(this.key));
            }
        }
        else if (random == 1){
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor2.path().name()));
                actor2.tell(new Actor2.Increment(this.key));
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor2.path().name()));
                actor2.tell(new Actor2.Display(this.key));
            }
        }
        else {
            int nType = (int)(Math.random()*2);
            String nodeType = nType == 0 ? "control-node" : "data-node";
            ActorRef<Actor2.Command> actor3 =
                    getContext().spawn(Actor2.create((int)(1+Math.random()*100), nodeType, vis), String.format("Actor-%d", this.index++));

            random = (int)(Math.random()*2);
            if (random == 0) {
                getContext().getLog().info(String.format("Sending ping message to %s", actor1.path().name()));
                actor1.tell(new Actor2.PingActor(this.key, actor3));
            }
            else {
                getContext().getLog().info(String.format("Sending ping message to %s", actor2.path().name()));
                actor2.tell(new Actor2.PingActor(this.key, actor3));
            }
        }
        return this;
    }

    public Behavior<Command> kill(Kill m) {
        wrapper.notify(getContext().getSelf().path().name(), m);
        int random = (int)(Math.random()*2);
        ActorRef<Actor2.Command> actorToKill = random == 0 ? actor1 : actor2;
        getContext().getLog().info(String.format("%s killed", actorToKill.path().name()));
        vis.destroy(actorToKill.path().name());
        getContext().stop(actorToKill);
        return this;
    }
}
