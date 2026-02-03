package com.apipratudo.reconciliation.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.util.StringUtils;

public final class TraceIdUtils {

  private TraceIdUtils() {
  }

  public static String resolveTraceId(HttpServletRequest request) {
    String traceId = request.getHeader("X-Request-Id");
    if (!StringUtils.hasText(traceId)) {
      traceId = request.getHeader("X-Cloud-Trace-Context");
      if (StringUtils.hasText(traceId) && traceId.contains("/")) {
        traceId = traceId.substring(0, traceId.indexOf('/'));
      }
    }
    if (!StringUtils.hasText(traceId)) {
      traceId = UUID.randomUUID().toString();
    }
    return traceId;
  }
}
