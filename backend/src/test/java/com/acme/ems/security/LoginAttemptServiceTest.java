package com.acme.ems.security;

import com.acme.ems.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {
  @Test void simultaneousAttemptsCannotBypassLimit() throws Exception {
    var limiter = new LoginAttemptService(5, 900, Clock.systemUTC());
    var accepted = new AtomicInteger();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(30)) {
      for (int i = 0; i < 30; i++) executor.submit(() -> {
        try { start.await(); limiter.check("nobody@example.com", "127.0.0.1"); accepted.incrementAndGet(); }
        catch (ApiException blocked) { assertEquals("LOGIN_RATE_LIMITED", blocked.code()); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      });
      start.countDown();
    }
    assertEquals(5, accepted.get());
  }

  @Test void successfulLoginResetsFailures() {
    var limiter = new LoginAttemptService(1, 900, Clock.systemUTC());
    limiter.check("user@example.com", "127.0.0.1");
    limiter.succeeded("user@example.com", "127.0.0.1");
    assertDoesNotThrow(() -> limiter.check("user@example.com", "127.0.0.1"));
  }

  @Test void addressCapacityIsBoundedAndExpiredWindowsAreReclaimed() {
    Clock clock = mock(Clock.class);
    Instant start = Instant.parse("2026-10-14T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var limiter = new LoginAttemptService(5, 900, clock);
    for (int i = 0; i < 10_000; i++) limiter.check("user@example.com", "address-" + i);
    assertThrows(ApiException.class, () -> limiter.check("user@example.com", "new-address"));
    when(clock.instant()).thenReturn(start.plusSeconds(900));
    assertDoesNotThrow(() -> limiter.check("user@example.com", "new-address"));
  }
}
