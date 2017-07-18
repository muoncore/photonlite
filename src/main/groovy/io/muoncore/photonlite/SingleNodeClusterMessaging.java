package io.muoncore.photonlite;

import io.muoncore.protocol.event.Event;

import java.util.Collections;
import java.util.Map;

public class SingleNodeClusterMessaging implements ClusterMessaging {
    @Override
    public void dispatch(Event event) {

    }

    @Override
    public void start(EventDistribution distribution) {

    }

    @Override
    public Map getStats() {
        return Collections.singletonMap("type", "single-node");
    }
}
