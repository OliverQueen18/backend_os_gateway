-- Allow cancelling transactions of a given operation type from the console / apps.
ALTER TABLE operation_types
    ADD COLUMN IF NOT EXISTS cancellable BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN operation_types.cancellable IS
    'When true, PENDING/QUEUED transactions of this type can be cancelled from the UI';
