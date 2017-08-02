package io.muoncore.photonlite.h2

import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

@Profile("h2")
interface JpaEventRepo extends JpaRepository<EventRecord, Long> {
    List<EventRecord> findAllByStreamAndOrderIdGreaterThan(String stream, long id)
    Long deleteByStream(String stream)
    @Query("select distinct t.stream from EventRecord t")
    List<String> findAllDistinctStream()
}
