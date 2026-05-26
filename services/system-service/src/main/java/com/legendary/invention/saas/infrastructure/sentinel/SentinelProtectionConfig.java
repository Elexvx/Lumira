package com.legendary.invention.saas.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelProtectionConfig {

    private final Environment environment;

    public SentinelProtectionConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        FlowRuleManager.loadRules(defaultRules());
    }

    private List<FlowRule> defaultRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(rule("public-branding-settings", qps("saas.traffic.system.public-branding-settings-qps", 80)));
        rules.add(rule("public-agreement-settings", qps("saas.traffic.system.public-agreement-settings-qps", 80)));
        rules.add(rule("public-security-settings", qps("saas.traffic.system.public-security-settings-qps", 60)));
        rules.add(rule("public-login-capabilities", qps("saas.traffic.system.public-login-capabilities-qps", 100)));
        rules.add(rule("public-captcha-challenge", qps("saas.traffic.system.public-captcha-challenge-qps", 80)));
        rules.add(rule("public-captcha-slider-verify", qps("saas.traffic.system.public-captcha-slider-verify-qps", 40)));
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
