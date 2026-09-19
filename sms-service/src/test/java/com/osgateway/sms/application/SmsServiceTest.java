package com.osgateway.sms.application;

import com.osgateway.common.enums.Priority;
import com.osgateway.sms.domain.SmsMessage;
import com.osgateway.sms.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.amqp.core.MessagePostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {
    @Mock SmsRepository smsRepository;
    @Mock SmsHistoryRepository historyRepository;
    @Mock AutoReplyRuleRepository ruleRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks SmsService smsService;

    @Test
    void send_persistsAndPublishes() {
        when(smsRepository.save(any())).thenAnswer(inv -> {
            SmsMessage m = inv.getArgument(0);
            m.setId(10L);
            return m;
        });
        SmsService.SendRequest req = new SmsService.SendRequest();
        req.setRecipient("70000000");
        req.setContent("Hello");
        req.setPriority(Priority.URGENT);
        SmsMessage result = smsService.send(req);
        assertEquals(10L, result.getId());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }
}