package com.apipratudo.gateway.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class TraceIdUtils {

  private TraceIdUtils() {
  }

  public static String currentTraceId() {
    String traceId = firstNonBlank(
        MDC.get("traceId"),
        MDC.get("trace_id"),
        MDC.get("X-B3-TraceId")
    );
    return normalize(traceId);
  }

  public static String resolveTraceId(HttpServletRequest request) {
    String mdcTraceId = currentTraceId();
    if (StringUtils.hasText(mdcTraceId)) {
      return mdcTraceId;
    }
    if (request == null) {
      return null;
    }
    String cloudTrace = extractCloudTraceId(request.getHeader("X-Cloud-Trace-Context"));
    if (StringUtils.hasText(cloudTrace)) {
      return cloudTrace;
    }
    String b3 = normalize(request.getHeader("X-B3-TraceId"));
    if (StringUtils.hasText(b3)) {
      return b3;
    }
    String traceParent = extractTraceParent(request.getHeader("traceparent"));
    if (StringUtils.hasText(traceParent)) {
      return traceParent;
    }
    return null;
  }

  private static String extractCloudTraceId(String header) {
    if (!StringUtils.hasText(header)) {
      return null;
    }
    int slash = header.indexOf('/');
    String traceId = slash > 0 ? header.substring(0, slash) : header;
    return normalize(traceId);
  }

  private static String extractTraceParent(String header) {
    if (!StringUtils.hasText(header)) {
      return null;
    }
    String[] parts = header.split("-");
    if (parts.length < 4) {
      return null;
    }
    return normalize(parts[1]);
  }

  private static String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }
}
