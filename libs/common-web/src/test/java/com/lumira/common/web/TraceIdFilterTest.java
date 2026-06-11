package com.lumira.common.web;

import com.lumira.common.constant.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void generatesTraceAndRequestIdsWhenHeadersAreMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HeaderConstants.REQUEST_ID)).isNotBlank();
        assertThat(response.getHeader(HeaderConstants.TRACE_ID)).isNotBlank();
        assertThat(TraceContext.getRequestId()).isNull();
        assertThat(TraceContext.getTraceId()).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void keepsIncomingHeadersAndClearsMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.REQUEST_ID, "request-1");
        request.addHeader(HeaderConstants.TRACE_ID, "trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HeaderConstants.REQUEST_ID)).isEqualTo("request-1");
        assertThat(response.getHeader(HeaderConstants.TRACE_ID)).isEqualTo("trace-1");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void canUseRequestIdAsTraceFallback() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(true, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.REQUEST_ID, "request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HeaderConstants.REQUEST_ID)).isEqualTo("request-1");
        assertThat(response.getHeader(HeaderConstants.TRACE_ID)).isEqualTo("request-1");
    }
}
