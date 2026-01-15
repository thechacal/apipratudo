package com.apipratudo.maismilionaria.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class TraceIdUtils {

  private TraceIdUtils() {
  }

  public static String resolveTraceId(HttpServletRequest request) {
    if (request == null) {
      return "-";
    }
    String traceId = firstNonBlank(
        request.getHeader("X-Trace-Id"),
        request.getHeader("X-Request-Id"),
        extractCloudTraceId(request.getHeader("X-Cloud-Trace-Context")),
        request.getHeader("X-B3-TraceId")
    );
    return StringUtils.hasText(traceId) ? traceId : "-";
  }

  private static String extractCloudTraceId(String header) {
    if (!StringUtils.hasText(header)) {
      return null;
    }
    int slash = header.indexOf('/');
    String traceId = slash > 0 ? header.substring(0, slash) : header;
    return StringUtils.hasText(traceId) ? traceId : null;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return null;
  }
}
