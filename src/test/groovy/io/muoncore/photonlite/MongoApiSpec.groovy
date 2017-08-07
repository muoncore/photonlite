package io.muoncore.photonlite

import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("mongo")
class MongoApiSpec extends PhotonApiSpec {
    List getColdItemCounts() {
        [12, 120, 1200, 10000]
    }
}
