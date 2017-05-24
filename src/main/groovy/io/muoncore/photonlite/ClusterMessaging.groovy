package io.muoncore.photonlite

import io.muoncore.protocol.event.Event

interface ClusterMessaging {
    void dispatch(Event event)
    void start(EventDistribution distribution)
}
