package com.acme.ems.exception;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestSizeFilterTest {
  @Test void unknownLengthBodyCannotBypassLimit() throws Exception {
    var request = unknownLength("x".repeat(1025));
    request.setAttribute(RequestIdFilter.ATTRIBUTE, "chunked-test");
    var response = new MockHttpServletResponse();
    new RequestSizeFilter(1024, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build())
        .doFilter(request, response, (req, res) -> fail("Oversized body reached controller"));
    assertEquals(413, response.getStatus());
    assertTrue(response.getContentAsString().contains("PAYLOAD_TOO_LARGE"));
    assertTrue(response.getContentAsString().contains("chunked-test"));
  }

  @Test void bodyAtLimitIsPreservedForController() throws Exception {
    String body = "x".repeat(1024);
    new RequestSizeFilter(1024, new ObjectMapper()).doFilter(unknownLength(body),
        new MockHttpServletResponse(), (req, res) ->
          assertEquals(body, new String(((HttpServletRequest) req).getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
  }

  private MockHttpServletRequest unknownLength(String body) {
    var request = new MockHttpServletRequest() {
      @Override public long getContentLengthLong() { return -1; }
      @Override public int getContentLength() { return -1; }
    };
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }
}
