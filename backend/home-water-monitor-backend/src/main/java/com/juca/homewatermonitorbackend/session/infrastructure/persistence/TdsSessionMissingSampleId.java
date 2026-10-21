package com.juca.homewatermonitorbackend.session.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TdsSessionMissingSampleId implements Serializable {
    private UUID sessionId;
    private int seq;
}
