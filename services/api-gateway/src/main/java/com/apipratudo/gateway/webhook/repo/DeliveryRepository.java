package com.apipratudo.gateway.webhook.repo;

import com.apipratudo.gateway.webhook.model.Delivery;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

  Delivery save(Delivery delivery);

  Optional<Delivery> findById(String id);

  List<Delivery> findAll();
}
