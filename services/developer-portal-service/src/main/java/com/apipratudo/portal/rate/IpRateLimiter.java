package com.apipratudo.portal.rate;

import com.apipratudo.portal.config.RateLimitProperties;
import com.apipratudo.portal.error.RateLimitException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class IpRateLimiter {

  private final RateLimitProperties properties;
  private final Clock clock;
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

  public IpRateLimiter(RateLimitProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public void assertAllowed(String ip) {
    if (!tryConsume(ip)) {
      throw new RateLimitException("KEY_CREATION_LIMIT", "Rate limit exceeded for IP");
    }
  }

  private synchronized boolean tryConsume(String ip) {
    int limit = properties.getIp().getRequestsPerMinute();
    Instant bucket = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);
    Window window = windows.get(ip);
    if (window == null || !bucket.equals(window.bucket())) {
      windows.put(ip, new Window(bucket, 1));
      return true;
    }
    if (window.count() >= limit) {
      return false;
    }
    windows.put(ip, new Window(bucket, window.count() + 1));
    return true;
  }

  private record Window(Instant bucket, int count) {
  }
}
