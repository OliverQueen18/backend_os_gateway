package com.osgateway.ussd.application;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableSubstitutionTest {
    @Test
    void substitutesVariables() {
        String result = VariableSubstitution.apply(
                "*144*1*{{phone}}*{{amount}}*{{pin}}#",
                Map.of("phone", "70000000", "amount", "1000", "pin", "1234"));
        assertEquals("*144*1*70000000*1000*1234#", result);
    }

    @Test
    void stripsCountryDialCodeFromPhone() {
        String result = VariableSubstitution.apply(
                "*145*1*{{phone}}*{{amount}}#",
                Map.of("phone", "+22370123456", "amount", "500"));
        assertEquals("*145*1*70123456*500#", result);
    }
}