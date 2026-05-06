package com.legendary.invention.auth.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AuthSentinelConfig {

    @PostConstruct
    public void init() {
        FlowRuleManager.loadRules(defaultRules());
    }

    private List<FlowRule> defaultRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(rule("auth-login", 30));
        rules.add(rule("auth-login-code-challenge", 30));
        rules.add(rule("auth-login-code-complete", 30));
        rules.add(rule("auth-second-factor-complete", 30));
        rules.add(rule("auth-refresh-token", 100));
        rules.add(rule("auth-current-user", 300));
        return rules;
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
