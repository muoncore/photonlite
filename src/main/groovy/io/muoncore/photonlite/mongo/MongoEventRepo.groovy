package io.muoncore.photonlite.mongo

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.repository.MongoRepository

@Profile("mongo")
interface MongoEventRepo extends MongoRepository<EventRecord, Long> {

    Page<EventRecord> findByStreamNameAndOrderIdGreaterThanEqual(String stream, long orderId, Pageable pageable)
    List<EventRecord> findByStreamNameAndOrderIdGreaterThanEqual(String stream, long orderId)
}
