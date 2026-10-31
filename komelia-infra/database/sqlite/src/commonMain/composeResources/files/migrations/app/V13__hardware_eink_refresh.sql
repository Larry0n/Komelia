ALTER TABLE ImageReaderSettings
    ADD COLUMN hardware_eink_refresh BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE ImageReaderSettings
    ADD COLUMN hardware_eink_refresh_mode TEXT DEFAULT 'SYSTEM' NOT NULL;
