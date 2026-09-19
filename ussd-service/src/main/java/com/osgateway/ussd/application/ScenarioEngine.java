package com.osgateway.ussd.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.ussd.domain.UssdAction;
import com.osgateway.ussd.domain.UssdStep;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ScenarioEngine {

    @Data @Builder
    public static class ScenarioResult {
        private List<String> commands;
        private Map<String, String> extracted;
        private boolean valid;
        private String message;
    }

    public ScenarioResult execute(List<UssdStep> steps, Map<String, String> variables, Map<String, String> screenResponses) {
        List<String> commands = new ArrayList<>();
        Map<String, String> vars = new HashMap<>(variables);
        Map<String, String> extracted = new HashMap<>();

        for (UssdStep step : steps) {
            UssdAction action;
            try {
                action = UssdAction.valueOf(step.getAction());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.SCENARIO_ERROR, "Unknown action: " + step.getAction());
            }
            String expression = VariableSubstitution.apply(step.getExpression(), vars);
            switch (action) {
                case COMPOSE -> commands.add(expression);
                case REPLY -> commands.add(expression);
                case WAIT -> commands.add("WAIT:" + (step.getWaitMillis() != null ? step.getWaitMillis() : 1000));
                case WAIT_SMS -> commands.add("WAIT_SMS:" + (step.getWaitMillis() != null ? step.getWaitMillis() : 60_000));
                case CONTINUE -> commands.add("CONTINUE");
                case READ -> {
                    String screen = screenResponses.getOrDefault(String.valueOf(step.getStepOrder()), "");
                    if (step.getExpectedPattern() != null && !screen.matches(step.getExpectedPattern())) {
                        return ScenarioResult.builder().commands(commands).extracted(extracted)
                                .valid(false).message("READ validation failed at step " + step.getStepOrder()).build();
                    }
                }
                case VALIDATE -> {
                    String screen = screenResponses.getOrDefault(String.valueOf(step.getStepOrder()), "");
                    if (step.getExpectedPattern() != null && !screen.matches("(?s).*" + step.getExpectedPattern() + ".*")) {
                        return ScenarioResult.builder().commands(commands).extracted(extracted)
                                .valid(false).message("VALIDATE failed at step " + step.getStepOrder()).build();
                    }
                }
                case EXTRACT -> {
                    String screen = screenResponses.getOrDefault(String.valueOf(step.getStepOrder()), "");
                    if (step.getExpectedPattern() != null && step.getExtractVar() != null) {
                        var m = java.util.regex.Pattern.compile(step.getExpectedPattern()).matcher(screen);
                        if (m.find()) {
                            String value = m.groupCount() >= 1 ? m.group(1) : m.group();
                            extracted.put(step.getExtractVar(), value);
                            vars.put(step.getExtractVar(), value);
                        }
                    }
                }
            }
        }
        return ScenarioResult.builder().commands(commands).extracted(extracted).valid(true).message("OK").build();
    }
}