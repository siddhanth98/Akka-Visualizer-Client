package com.test;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import vis.MyVisualizerClient;

public class Guardian extends AbstractBehavior<Guardian.Command> {
    interface Command {}
    private ActorRef<Actor1.Command> actor1;
    private ActorRef<Actor1.Command> actor2;
    private int index = 3;
    private final MyVisualizerClient vis = new MyVisualizerClient();

    public static class Spawn implements Command {}
    public static class InitiateMessageTransfer implements Command {}
    public static class ScheduleMessage implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new Guardian(context));
    }

    private Guardian(ActorContext<Command> context) {
        super(context);
        context.getLog().info(String.format("%s created%n", context.getSelf().path().toString()));
        vis.submit(context.getSelf().path().toString());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Spawn.class, m -> this.handleSpawn())
                .onMessage(InitiateMessageTransfer.class, m -> this.handleMessageTransfer())
                .onMessage(ScheduleMessage.class, m -> this.handleSchedule())
                .onSignal(Terminated.class, sig -> {
                    vis.destroy(getContext().getSelf().path().toString());
                    return Behaviors.stopped();
                })
                .build();
    }

    private Behavior<Command> handleSpawn() {
        actor1 = getContext().spawn(Actor1.create(10), "Actor-1");
        actor2 = getContext().spawn(Actor1.create(0), "Actor-2");
        return this;
    }

    private Behavior<Command> handleMessageTransfer() {
        actor1.tell(new Actor1.Increment());
        vis.send("increment", getContext().getSelf().path().toString(), actor1.path().toString());

        actor2.tell(new Actor1.Increment());
        vis.send("increment", getContext().getSelf().path().toString(), actor2.path().toString());
        return this;
    }

    private Behavior<Command> handleSchedule() {
        int random = (int)(Math.random()*3);
        int messageToSend = (int)(Math.random()*2);

        if (random == 0) {
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.Increment());
                vis.send("increment", getContext().getSelf().path().toString(), actor1.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.Display());
                vis.send("display", getContext().getSelf().path().toString(), actor1.path().toString());
            }
        }
        else if (random == 1){
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.Increment());
                vis.send("increment", getContext().getSelf().path().toString(), actor2.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.Display());
                vis.send("display", getContext().getSelf().path().toString(), actor2.path().toString());
            }
        }
        else {
            ActorRef<Actor1.Command> actor3 =
                    getContext().spawn(Actor1.create((int)(1+Math.random()*100)), String.format("Actor-%d", this.index++));

            random = (int)(Math.random()*2);
            if (random == 0) {
                getContext().getLog().info(String.format("Sending ping message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.PingActor(actor3));
                vis.send("ping", getContext().getSelf().path().toString(), actor1.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending ping message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.PingActor(actor3));
                vis.send("ping", getContext().getSelf().path().toString(), actor2.path().toString());
            }
        }
        return this;
    }
}
