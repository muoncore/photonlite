package io.muoncore.photonlite.h2

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Lob

@Entity
class EventRecord {
    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    Long orderId
    Long eventTime

    String stream

//    @Column(name="PAYLOAD", columnDefinition="CLOB NOT NULL")
    @Lob
    byte[] payload
    String encoding
}
