-- Allow WAIT_SMS action on USSD scenario steps (SMS confirmation after interactive USSD).
ALTER TABLE ussd_steps DROP CONSTRAINT IF EXISTS ussd_steps_action_check;
ALTER TABLE ussd_steps ADD CONSTRAINT ussd_steps_action_check
    CHECK (action IN (
        'COMPOSE', 'READ', 'REPLY', 'WAIT', 'CONTINUE', 'VALIDATE', 'EXTRACT', 'WAIT_SMS'
    ));
