package io.muoncore.photonlite

import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("gcpdatastore")
class GCPDatastoreApiSpec extends PhotonApiSpec {
    List getColdItemCounts() {
        [12]
    }

    List getItemCounts() {
         [12]
    }
}
