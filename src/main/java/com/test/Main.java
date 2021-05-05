package com.test;

import akka.actor.typed.ActorSystem;
import vis.MyVisualizerClient;

public class Main {
    public static void main(String[] args) {
        MyVisualizerClient vis = new MyVisualizerClient(); /* just maintain a single vis object */
        ActorSystem.create(Guardian.create(vis), "Guardian");
    }
}
