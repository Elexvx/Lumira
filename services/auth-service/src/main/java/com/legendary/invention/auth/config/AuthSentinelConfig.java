package com.legendary.invention.auth.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AuthSentinelConfig {

    private final Environment environment;

    public AuthSentinelConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        FlowRuleManager.loadRules(defaultRules());
    }

    private List<FlowRule> defaultRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(rule("auth-login", qps("saas.traffic.auth.login-qps", 20)));
        rules.add(rule("auth-login-code-challenge", qps("saas.traffic.auth.login-code-challenge-qps", 20)));
        rules.add(rule("auth-login-code-complete", qps("saas.traffic.auth.login-code-complete-qps", 20)));
        rules.add(rule("auth-second-factor-complete", qps("saas.traffic.auth.second-factor-complete-qps", 20)));
        rules.add(rule("auth-refresh-token", qps("saas.traffic.auth.refresh-token-qps", 80)));
        rules.add(rule("auth-current-user", qps("saas.traffic.auth.current-user-qps", 160)));
        return rules;
    }

    private double qps(String property, double defaultValue) {
        return environment.getProperty(property, Double.class, defaultValue);
    }

    private FlowRule rule(String resource, double qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setLimitApp("default");
        return rule;
    }
}
