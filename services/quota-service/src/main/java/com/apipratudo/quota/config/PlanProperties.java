package com.apipratudo.quota.config;

import com.apipratudo.quota.dto.ApiKeyLimits;
import com.apipratudo.quota.model.Plan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.plans")
@Validated
public class PlanProperties {

  @Valid
  private PlanLimits free = new PlanLimits(30, 200);

  @Valid
  private PlanLimits premium = new PlanLimits(600, 50000);

  public PlanLimits getFree() {
    return free;
  }

  public void setFree(PlanLimits free) {
    this.free = free;
  }

  public PlanLimits getPremium() {
    return premium;
  }

  public void setPremium(PlanLimits premium) {
    this.premium = premium;
  }

  public ApiKeyLimits limitsFor(Plan plan) {
    PlanLimits target = plan == Plan.PREMIUM ? premium : free;
    return new ApiKeyLimits(target.getRequestsPerMinute(), target.getRequestsPerDay());
  }

  public static class PlanLimits {
    @Min(1)
    private int requestsPerMinute;

    @Min(1)
    private int requestsPerDay;

    public PlanLimits() {
    }

    public PlanLimits(int requestsPerMinute, int requestsPerDay) {
      this.requestsPerMinute = requestsPerMinute;
      this.requestsPerDay = requestsPerDay;
    }

    public int getRequestsPerMinute() {
      return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
      this.requestsPerMinute = requestsPerMinute;
    }

    public int getRequestsPerDay() {
      return requestsPerDay;
    }

    public void setRequestsPerDay(int requestsPerDay) {
      this.requestsPerDay = requestsPerDay;
    }
  }
}
