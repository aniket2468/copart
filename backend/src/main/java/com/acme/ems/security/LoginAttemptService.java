package com.acme.ems.security;

import com.acme.ems.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
  private record Attempt(int failures, Instant windowStarted) {}

  private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
  private final int maximumFailures;
  private final long windowSeconds;
  private final Clock clock;
  private static final int MAXIMUM_TRACKED_ADDRESSES = 10_000;

  LoginAttemptService(@Value("${app.login.max-failures:5}") int maximumFailures,
      @Value("${app.login.window-seconds:900}") long windowSeconds, Clock clock) {
    this.maximumFailures = maximumFailures;
    this.windowSeconds = windowSeconds;
    this.clock = clock;
  }

  public synchronized void check(String email, String remoteAddress) {
    String key = key(email, remoteAddress);
    Instant now = Instant.now(clock);
    if (!attempts.containsKey(key) && attempts.size() >= MAXIMUM_TRACKED_ADDRESSES) {
      attempts.entrySet().removeIf(entry -> expired(entry.getValue(), now));
      if (attempts.size() >= MAXIMUM_TRACKED_ADDRESSES) {
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
            "Login is temporarily busy; try again later");
      }
    }
    AtomicBoolean blocked = new AtomicBoolean();
    attempts.compute(key, (ignored, current) -> {
      if (current == null || expired(current, now)) return new Attempt(1, now);
      if (current.failures() >= maximumFailures) {
        blocked.set(true);
        return current;
      }
      return new Attempt(current.failures() + 1, current.windowStarted());
    });
    if (blocked.get()) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
          "Too many failed login attempts; try again later");
    }
  }

  public void failed(String email, String remoteAddress) {
    // The attempt is reserved atomically by check() before authentication starts.
  }

  public synchronized void succeeded(String email, String remoteAddress) {
    attempts.remove(key(email, remoteAddress));
  }

  private boolean expired(Attempt attempt, Instant now) {
    return !now.isBefore(attempt.windowStarted().plusSeconds(windowSeconds));
  }

  private String key(String email, String remoteAddress) {
    return remoteAddress == null ? "unknown" : remoteAddress;
  }
}
