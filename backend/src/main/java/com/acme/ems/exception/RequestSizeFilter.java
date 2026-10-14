package com.acme.ems.exception;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {
  private final long maximumBodyBytes;
  private final ObjectMapper mapper;

  RequestSizeFilter(@Value("${app.request.max-body-bytes:1048576}") long maximumBodyBytes,
      ObjectMapper mapper) {
    if (maximumBodyBytes < 1 || maximumBodyBytes >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Request body limit must be between 1 and 2147483646 bytes");
    }
    this.maximumBodyBytes = maximumBodyBytes;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    byte[] body = request.getContentLengthLong() > maximumBodyBytes ? null
        : request.getInputStream().readNBytes((int) maximumBodyBytes + 1);
    if (body == null || body.length > maximumBodyBytes) {
      String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      response.setCharacterEncoding("UTF-8");
      response.setContentType("application/json");
      mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(Instant.now(), 413,
          "PAYLOAD_TOO_LARGE", "Request body exceeds the allowed size", Map.of(), requestId));
      return;
    }
    chain.doFilter(new HttpServletRequestWrapper(request) {
      @Override public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
          @Override public int read() { return input.read(); }
          @Override public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
          }
          @Override public boolean isFinished() { return input.available() == 0; }
          @Override public boolean isReady() { return true; }
          @Override public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Asynchronous request body reads are not supported");
          }
        };
      }
      @Override public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        return new BufferedReader(new InputStreamReader(getInputStream(),
            encoding == null ? StandardCharsets.UTF_8.name() : encoding));
      }
    }, response);
  }
}
