package com.apipratudo.billingsaas.dto;

import com.apipratudo.billingsaas.model.ChargeStatus;
import java.time.Instant;

public class ChargeStatusResponse {

  private String id;
  private ChargeStatus status;
  private Instant paidAt;
  private PixDataResponse pix;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ChargeStatus getStatus() {
    return status;
  }

  public void setStatus(ChargeStatus status) {
    this.status = status;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  public PixDataResponse getPix() {
    return pix;
  }

  public void setPix(PixDataResponse pix) {
    this.pix = pix;
  }
}
