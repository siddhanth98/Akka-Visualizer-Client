package com.vis;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vis.Message;
import vis.MyVisualizerClient;


import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Main class to test the interceptor
 * @author Siddhanth Venkateshwaran
 */
public class Main {
    static Logger logger;
    static MyVisualizerClient vis;
    static MyVisualizerClient.MessageWrapper wrapper;
    static String actor1, actor2, actor3;
    static Config config;

    public static final class TestMessage implements Message {
        long key;
        public TestMessage(long key) {
            this.key = key;
        }
        public long getSenderKey() {
            return this.key;
        }
    }

    @BeforeClass
    public static void init() {
        logger = LoggerFactory.getLogger(Main.class);
        vis = new MyVisualizerClient();
        config = ConfigFactory.parseFile(new File("src/main/resources/input.conf"));
        actor1 = config.getString("conf.tests.actor1");
        actor2 = config.getString("conf.tests.actor2");
        actor3 = config.getString("conf.tests.actor3");
    }

    @Before
    public void initVis() {
        vis = new MyVisualizerClient();
        wrapper = vis.new MessageWrapper();
    }

    @Test
    public void keysShouldBeUnique() {
        long key1 = vis.submit("actor-1");
        long key2 = vis.submit("actor-2");
        assertNotEquals(key1, key2);
    }

    @Test
    public void timestampsShouldBeNonDecreasing() {
        long ts1, ts2, key1, key2;

        key1 = vis.submit(actor1);
        vis.submit(actor2);
        key2 = vis.submit(actor3);

        wrapper.notify(actor2, new TestMessage(key1));
        ts1 = wrapper.getTimestamp();

        wrapper.notify(actor2, new TestMessage(key2));
        ts2 = wrapper.getTimestamp();

        assertTrue(ts1 <= ts2);
    }

    @Test
    public void destroyedActorsShouldNotExist() {
        Map<String, Long> invertedKeyRef;

        vis.submit(actor1);
        vis.submit(actor2);
        vis.submit(actor3);

        invertedKeyRef = vis.getInvertedKeyRef();
        logger.info(String.format("Inverted (actorName to key) map => %s", invertedKeyRef.toString()));

        vis.destroy(actor2);
        vis.destroy(actor3);

        invertedKeyRef = vis.getInvertedKeyRef();
        logger.info(String.format("Inverted (actorName to key) map => %s", invertedKeyRef.toString()));

        assertTrue(
                invertedKeyRef.containsKey(actor1) &&
                !invertedKeyRef.containsKey(actor2) &&
                !invertedKeyRef.containsKey(actor3)
        );
    }
}
