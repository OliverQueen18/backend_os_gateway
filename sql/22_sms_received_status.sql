-- Allow RECEIVED status for gateway inbound SMS reports.
ALTER TABLE sms DROP CONSTRAINT IF EXISTS sms_status_check;
ALTER TABLE sms ADD CONSTRAINT sms_status_check
    CHECK (status IN (
        'PENDING', 'QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'FAILED', 'SCHEDULED', 'RECEIVED'
    ));
