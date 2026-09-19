package com.osgateway.ussd.application;

import com.osgateway.common.util.PhoneNumbers;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableSubstitution {
    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private VariableSubstitution() {}

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) return null;
        Matcher matcher = VAR.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.getOrDefault(key, "");
            if ("phone".equalsIgnoreCase(key)) {
                String national = PhoneNumbers.toNationalDigits(value);
                value = national != null ? national : "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
