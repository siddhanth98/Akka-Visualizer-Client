package com.chat;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import vis.MyVisualizerClient;

import java.io.File;
import java.util.List;

public class Main {

    private static long key;

    public static Behavior<Void> create(MyVisualizerClient vis) {
        Config config = ConfigFactory.parseFile(new File("src/main/resources/input.conf"));
        int numClients = config.getInt("conf.clients");
        List<String> nodeTypes = config.getStringList("conf.groups");

        return Behaviors.setup(context -> {
            key = vis.submit(context.getSelf().path().name());
            ActorRef<ChatRoom.RoomCommand> chatRoom = context.spawn(ChatRoom.create(vis), "chat-room");
            for (int i = 0; i < numClients; i++) {
                String group = nodeTypes.get((int)(Math.random()*nodeTypes.size()));
                ActorRef<ChatRoom.SessionEvent> client = context.spawn(Client.create(vis, group), String.format("client-%d", i));
                chatRoom.tell(new ChatRoom.GetSession(key, String.format("session-%d", i), client));
            }

           return Behaviors.receive(Void.class)
                   .onSignal(Terminated.class, sig -> {
                       vis.destroy(context.getSelf().path().name());
                       return Behaviors.stopped();
                   }).build();
        });
    }

    public static void main(String[] args) {
        ActorSystem.create(Main.create(new MyVisualizerClient()), "Guardian");
    }
}
