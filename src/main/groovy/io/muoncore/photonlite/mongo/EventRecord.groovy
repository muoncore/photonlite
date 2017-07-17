package io.muoncore.photonlite.mongo

import com.google.gson.annotations.SerializedName
import io.muoncore.codec.Codecs
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document
class EventRecord {
    @Id
    Long orderId
    Long eventTime

    @Indexed
    String streamName

    String eventType

    String schema

    Long causedById

    String causedByRelation

    String service
    Map payload
}
