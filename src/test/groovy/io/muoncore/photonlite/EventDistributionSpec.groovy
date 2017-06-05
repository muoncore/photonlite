package io.muoncore.photonlite

import spock.lang.Specification

class EventDistributionSpec extends Specification {


    def "no longer calls a StreamObserver from the list when it notifies completion"() {
        given:
        def ed = new EventDistribution(persistence: Mock(Persistence), clusterMessaging: Mock(ClusterMessaging))
        def obs = ed.subscribeToLive("hello", "hot", 0)

        when: "observer marks as completed"

        then: "no events are notified to the observer, as it is removed from the list"

    }
}
