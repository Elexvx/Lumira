package com.legendary.invention.gateway;

import com.legendary.invention.common.constant.HeaderConstants;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = firstText(
                exchange.getRequest().getHeaders().getFirst(HeaderConstants.REQUEST_ID),
                exchange.getRequest().getId()
        );
        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid()
                ? spanContext.getTraceId()
                : firstText(exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACE_ID), requestId);
        String spanId = spanContext.isValid() ? spanContext.getSpanId() : "";

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers((headers) -> {
                    headers.set(HeaderConstants.REQUEST_ID, requestId);
                    headers.set(HeaderConstants.TRACE_ID, traceId);
                })
                .build();

        exchange.getResponse().getHeaders().set(HeaderConstants.REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set(HeaderConstants.TRACE_ID, traceId);
        if (StringUtils.hasText(spanId)) {
            exchange.getResponse().getHeaders().set("X-Span-Id", spanId);
        }

        return chain.filter(exchange.mutate().request(request).build());
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }
}
