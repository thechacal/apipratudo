package com.apipratudo.webhook.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliveryOutboxRepository {

  OutboundDelivery save(OutboundDelivery delivery);

  Optional<OutboundDelivery> findById(String id);

  List<OutboundDelivery> findDue(Instant now, int limit);

  List<OutboundDelivery> findAll();

  void deleteAll();
}
