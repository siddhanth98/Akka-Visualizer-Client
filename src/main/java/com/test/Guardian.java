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
    private ActorRef<Actor1.Command> actor1;
    private ActorRef<Actor1.Command> actor2;
    private int index = 3;
    private final MyVisualizerClient vis;

    interface Command {}

    public static class Spawn implements Command {}
    public static class InitiateMessageTransfer implements Command {}
    public static class ScheduleMessage implements Command {}
    public static class Kill implements Command {}

    public static Behavior<Command> create(MyVisualizerClient vis) {
        return Behaviors.setup(context -> new Guardian(context, vis));
    }

    private Guardian(ActorContext<Command> context, MyVisualizerClient vis) {
        super(context);
        this.vis = vis;
        context.getLog().info(String.format("%s created%n", context.getSelf().path().toString()));
        this.vis.submit(context.getSelf().path().toString()); /* submit actor ref to vis */
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Spawn.class, m -> this.handleSpawn())
                .onMessage(InitiateMessageTransfer.class, m -> this.handleMessageTransfer())
                .onMessage(ScheduleMessage.class, m -> this.handleSchedule())
                .onMessage(Kill.class, m -> this.kill())
                .onSignal(Terminated.class, sig -> {
                    vis.destroy(getContext().getSelf().path().toString()); /* tell vis to delete this actor node */
                    return Behaviors.stopped();
                }).build();
    }

    private Behavior<Command> handleSpawn() {
        actor1 = getContext().spawn(Actor1.create(10, "control-node", this.vis), "Actor-1");
        actor2 = getContext().spawn(Actor1.create(0, "data-node", this.vis), "Actor-2");
        return this;
    }

    private Behavior<Command> handleMessageTransfer() {
        actor1.tell(new Actor1.Increment());
        notifyMessageTransfer("increment", actor1.path().toString());

        actor2.tell(new Actor1.Increment());
        notifyMessageTransfer("increment", actor2.path().toString());
        return this;
    }

    private Behavior<Command> handleSchedule() {
        int random = (int)(Math.random()*3);
        int messageToSend = (int)(Math.random()*2);

        if (random == 0) {
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.Increment());
                notifyMessageTransfer("increment", actor1.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.Display());
                notifyMessageTransfer("display", actor1.path().toString());
            }
        }
        else if (random == 1){
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.Increment());
                notifyMessageTransfer("increment", actor2.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.Display());
                notifyMessageTransfer("display", actor2.path().toString());
            }
        }
        else {
            int nType = (int)(Math.random()*2);
            String nodeType = nType == 0 ? "control-node" : "data-node";
            ActorRef<Actor1.Command> actor3 =
                    getContext().spawn(Actor1.create((int)(1+Math.random()*100), nodeType, vis), String.format("Actor-%d", this.index++));

            random = (int)(Math.random()*2);
            if (random == 0) {
                getContext().getLog().info(String.format("Sending ping message to %s", actor1.path().toString()));
                actor1.tell(new Actor1.PingActor(actor3));
                notifyMessageTransfer("ping", actor1.path().toString());
            }
            else {
                getContext().getLog().info(String.format("Sending ping message to %s", actor2.path().toString()));
                actor2.tell(new Actor1.PingActor(actor3));
                notifyMessageTransfer("ping", actor2.path().toString());
            }
        }
        return this;
    }

    public Behavior<Command> kill() {
        int random = (int)(Math.random()*2);
        ActorRef<Actor1.Command> actorToKill = random == 0 ? actor1 : actor2;
        getContext().getLog().info(String.format("%s killed", actorToKill.path().toString()));
        vis.destroy(actorToKill.path().toString());
        getContext().stop(actorToKill);
        return this;
    }

    public void notifyMessageTransfer(String label, String to) {
        vis.send(label, getContext().getSelf().path().toString(), to);
    }
}
