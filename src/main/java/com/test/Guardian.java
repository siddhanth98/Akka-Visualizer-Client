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

public class Guardian extends AbstractBehavior<Guardian.Command> {
    private final long key;
    private ActorRef<Actor2.Command> actor1;
    private ActorRef<Actor2.Command> actor2;
    private int index = 3;
    private final MyVisualizerClient vis;

    interface Command {}

    public static class Spawn implements Command {}
    public static class InitiateMessageTransfer implements Command {}
    public static class ScheduleMessage implements Command {}
    public static class Kill implements Command {}

    public static Behavior<Command> create(MyVisualizerClient vis) {
        return Behaviors.setup(context -> {
            long key = vis.submit(context.getSelf().path().toString());
            return new Guardian(key, context, vis);
        });
    }

    private Guardian(long key, ActorContext<Command> context, MyVisualizerClient vis) {
        super(context);
        this.vis = vis;
        this.key = key;
        context.getLog().info(String.format("%s created%n", context.getSelf().path().toString()));
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
        actor1 = getContext().spawn(Actor2.create(10, "control-node", this.vis), "Actor-1");
        actor2 = getContext().spawn(Actor2.create(0, "data-node", this.vis), "Actor-2");
        return this;
    }

    private Behavior<Command> handleMessageTransfer() {
        Actor2.Increment msg = (Actor2.Increment)
                this.vis.new MessageWrapper(new Actor2.Increment(this.key), "send", actor1.path().toString()).getMessage();
        actor1.tell(msg);

        msg = (Actor2.Increment)
                this.vis.new MessageWrapper(new Actor2.Increment(this.key), "send", actor2.path().toString()).getMessage();
        actor2.tell(msg);
        return this;
    }

    private Behavior<Command> handleSchedule() {
        int random = (int)(Math.random()*3);
        int messageToSend = (int)(Math.random()*2);
        MyVisualizerClient.MessageWrapper wrapper = this.vis.new MessageWrapper();

        if (random == 0) {
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor1.path().toString()));
                notify(wrapper, actor1.path().toString(), "send", new Actor2.Increment(this.key));
                actor1.tell((Actor2.Increment)wrapper.getMessage());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor1.path().toString()));
                notify(wrapper, actor1.path().toString(), "send", new Actor2.Display(this.key));
                actor1.tell((Actor2.Display)wrapper.getMessage());
            }
        }
        else if (random == 1){
            if (messageToSend == 0) {
                getContext().getLog().info(String.format("Sending increment message to %s", actor2.path().toString()));
                notify(wrapper, actor2.path().toString(), "send", new Actor2.Increment(this.key));
                actor2.tell((Actor2.Increment)wrapper.getMessage());
            }
            else {
                getContext().getLog().info(String.format("Sending display message to %s", actor2.path().toString()));
                notify(wrapper, actor2.path().toString(), "send", new Actor2.Display(this.key));
                actor2.tell((Actor2.Display)wrapper.getMessage());
            }
        }
        else {
            int nType = (int)(Math.random()*2);
            String nodeType = nType == 0 ? "control-node" : "data-node";
            ActorRef<Actor2.Command> actor3 =
                    getContext().spawn(Actor2.create((int)(1+Math.random()*100), nodeType, vis), String.format("Actor-%d", this.index++));

            random = (int)(Math.random()*2);
            if (random == 0) {
                getContext().getLog().info(String.format("Sending ping message to %s", actor1.path().toString()));
                notify(wrapper, actor1.path().toString(), "send", new Actor2.PingActor(this.key, actor3));
                actor1.tell((Actor2.PingActor)wrapper.getMessage());
            }
            else {
                getContext().getLog().info(String.format("Sending ping message to %s", actor2.path().toString()));
                notify(wrapper, actor2.path().toString(), "send", new Actor2.PingActor(this.key, actor3));
                actor2.tell((Actor2.PingActor)wrapper.getMessage());
            }
        }
        return this;
    }

    public Behavior<Command> kill() {
        int random = (int)(Math.random()*2);
        ActorRef<Actor2.Command> actorToKill = random == 0 ? actor1 : actor2;
        getContext().getLog().info(String.format("%s killed", actorToKill.path().toString()));
        vis.destroy(actorToKill.path().toString());
        getContext().stop(actorToKill);
        return this;
    }

    public void notifyMessageTransfer(String label, String to) {
        vis.send(label, getContext().getSelf().path().toString(), to);
    }

    public void notify(MyVisualizerClient.MessageWrapper wrapper, String receiver, String event, Message msg) {
        wrapper.setReceiver(receiver);
        wrapper.setMessage(msg);
        wrapper.emit(event);
    }
}
