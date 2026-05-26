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
import org.springframework.core.env.Environment;
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
    private final Environment environment;

    public SentinelGatewayConfig(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer,
            Environment environment
    ) {
        this.viewResolversProvider = viewResolversProvider;
        this.serverCodecConfigurer = serverCodecConfigurer;
        this.environment = environment;
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
        rules.add(routeRule("auth-service", qps("saas.traffic.gateway.auth-service-qps", 120)));
        rules.add(routeRule("file-service", qps("saas.traffic.gateway.file-service-qps", 80)));
        rules.add(routeRule("message-service", qps("saas.traffic.gateway.message-service-qps", 80)));
        rules.add(routeRule("plugin-service", qps("saas.traffic.gateway.plugin-service-qps", 50)));
        rules.add(routeRule("localization-service", qps("saas.traffic.gateway.localization-service-qps", 80)));
        rules.add(routeRule("system-service", qps("saas.traffic.gateway.system-service-qps", 160)));
        return rules;
    }

    private double qps(String property, double defaultValue) {
        return environment.getProperty(property, Double.class, defaultValue);
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
