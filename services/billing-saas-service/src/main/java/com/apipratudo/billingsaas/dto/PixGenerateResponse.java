package com.apipratudo.billingsaas.dto;

import com.apipratudo.billingsaas.model.ChargeStatus;

public class PixGenerateResponse {

  private String chargeId;
  private ChargeStatus status;
  private PixDataResponse pix;
  private String whatsappLink;

  public String getChargeId() {
    return chargeId;
  }

  public void setChargeId(String chargeId) {
    this.chargeId = chargeId;
  }

  public ChargeStatus getStatus() {
    return status;
  }

  public void setStatus(ChargeStatus status) {
    this.status = status;
  }

  public PixDataResponse getPix() {
    return pix;
  }

  public void setPix(PixDataResponse pix) {
    this.pix = pix;
  }

  public String getWhatsappLink() {
    return whatsappLink;
  }

  public void setWhatsappLink(String whatsappLink) {
    this.whatsappLink = whatsappLink;
  }
}
