package com.test;

import akka.actor.typed.ActorSystem;
import vis.MyVisualizerClient;

import java.util.Timer;
import java.util.TimerTask;

public class Main {
    public static void main(String[] args) {
        MyVisualizerClient vis = new MyVisualizerClient(); /* just maintain a single vis object */

        final ActorSystem<Guardian.Command> system = ActorSystem.create(Guardian.create(vis), "Guardian");
        system.tell(new Guardian.Spawn());

        long startTime = System.currentTimeMillis() / 1000;
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                system.tell(new Guardian.ScheduleMessage());

                if (Math.abs(System.currentTimeMillis()/1000-startTime) >= 20) {
                    system.tell(new Guardian.Kill());
                    timer.cancel();
                }
            }
        };
        timer.schedule(timerTask, 3000, 1000);
    }
}
