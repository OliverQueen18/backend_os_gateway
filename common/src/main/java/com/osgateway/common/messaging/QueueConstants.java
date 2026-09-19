package com.osgateway.common.messaging;

public final class QueueConstants {

    public static final String EXCHANGE = "osgateway.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "osgateway.dlx";

    public static final String TRANSACTION_QUEUE = "osgateway.transaction.queue";
    public static final String TRANSACTION_ROUTING_KEY = "transaction.job";
    public static final String TRANSACTION_DLQ = "osgateway.transaction.dlq";

    public static final String SMS_QUEUE = "osgateway.sms.queue";
    public static final String SMS_ROUTING_KEY = "sms.job";
    public static final String SMS_DLQ = "osgateway.sms.dlq";

    public static final String NOTIFICATION_QUEUE = "osgateway.notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.job";
    public static final String NOTIFICATION_DLQ = "osgateway.notification.dlq";

    public static final String USSD_QUEUE = "osgateway.ussd.queue";
    public static final String USSD_ROUTING_KEY = "ussd.job";

    public static final String HEADER_PRIORITY = "x-priority";
    public static final int MAX_PRIORITY = 10;

    private QueueConstants() {
    }
}
