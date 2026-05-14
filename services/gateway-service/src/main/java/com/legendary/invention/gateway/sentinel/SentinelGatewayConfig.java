package com.legendary.invention.gateway.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class SentinelGatewayConfig {

    private final ObjectProvider<List<ViewResolver>> viewResolversProvider;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer
    ) {
        this.viewResolversProvider = viewResolversProvider;
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @PostConstruct
    public void initGatewaySentinelRules() {
        GatewayRuleManager.loadRules(defaultGatewayRules());
        GatewayCallbackManager.setBlockHandler(this::handleBlockedRequest);
    }

    @Bean
    @Order(-1)
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    @Order(-1)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        List<ViewResolver> viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    private Mono<ServerResponse> handleBlockedRequest(ServerWebExchange exchange, Throwable throwable) {
        ApiResponse<Void> body = ApiResponse.fail(
                ErrorCode.TRAFFIC_LIMITED,
                ErrorCode.TRAFFIC_LIMITED.getDefaultMessage(),
                ErrorCode.TRAFFIC_LIMITED.getDefaultUserMessage(),
                requestId(exchange),
                exchange.getRequest().getPath().value()
        );
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    private Set<GatewayFlowRule> defaultGatewayRules() {
        Set<GatewayFlowRule> rules = new LinkedHashSet<>();
        rules.add(routeRule("auth-service", 400));
        rules.add(routeRule("file-service", 200));
        rules.add(routeRule("message-service", 200));
        rules.add(routeRule("plugin-service", 120));
        rules.add(routeRule("localization-service", 120));
        rules.add(routeRule("system-service", 500));
        return rules;
    }

    private GatewayFlowRule routeRule(String routeId, double qps) {
        return new GatewayFlowRule(routeId)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setIntervalSec(1);
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getId();
        }
        return requestId;
    }
}
