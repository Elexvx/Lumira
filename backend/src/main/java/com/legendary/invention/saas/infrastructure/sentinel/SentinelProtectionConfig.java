package com.legendary.invention.saas.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelProtectionConfig {

    @PostConstruct
    public void init() {
        FlowRuleManager.loadRules(defaultRules());
    }

    private List<FlowRule> defaultRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(rule("public-branding-settings", 100));
        rules.add(rule("public-agreement-settings", 100));
        rules.add(rule("public-security-settings", 80));
        rules.add(rule("public-login-capabilities", 120));
        rules.add(rule("public-captcha-challenge", 120));
        rules.add(rule("public-captcha-slider-verify", 50));
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
