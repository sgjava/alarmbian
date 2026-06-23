-- ============================================================================
-- Copyright (c) Steven P. Goldsmith. All rights reserved.
-- Alarmbian NVR Database Schema Definition
-- Optimized for high-throughput multi-camera environments using H2.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Table: EVENT
-- Purpose: Primary transactional ledger capturing discrete device triggers,
--          hardware motion markers, background recording frames, and inbound
--          SMTP alert signals.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event (
    -- Unique sequential auto-incrementing identity primary key identifier.
    id IDENTITY PRIMARY KEY,
    
    -- The specific logical hardware configuration identifier for the camera.
    device_name VARCHAR(50) NOT NULL,
    
    -- Categorization token (e.g., 'MOTION_START', 'RECORD_START', 'SMTP_VEHICLE').
    event_type VARCHAR(50) NOT NULL,
    
    -- Absolute target disk storage file link, image coordinate string, or text metadata.
    event_data VARCHAR(255) NULL,
    
    -- System-assigned tracking timestamp mapping when the hardware event occurred.
    event_time TIMESTAMP NOT NULL
);

-- ----------------------------------------------------------------------------
-- Index: EVENT_DEVICE_NAME
-- Purpose: Composite index engineered to optimize relational database lookup 
--          speeds for workspace state changes, camera hot-swapping dropdowns,
--          and rapid timeline chronological pagination scans.
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS event_device_name 
ON event(device_name, event_type, event_time);


-- ----------------------------------------------------------------------------
-- Table: FRAME
-- Purpose: Intermediary mapping asset capturing explicit video or image frame
--          extractions targeted for downstream deep learning evaluation passes.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS frame (
    -- Unique sequential auto-incrementing primary key identifier.
    id IDENTITY PRIMARY KEY,
    
    -- Relational foreign key binding linking directly back to the source parent event.
    event_id BIGINT NOT NULL,
    
    -- Specialized precise timestamp mapping when the frame window slice was extracted.
    frame_time TIMESTAMP NOT NULL,
    
    -- Declarative relational constraint enforcing cascading data deletions to prevent orphaned records.
    CONSTRAINT fk_frame_event FOREIGN KEY (event_id) 
        REFERENCES event(id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Index: IDX_FRAME_EVENT_ID
-- Purpose: Explicit index tracking parent references. Prevents linear full-table 
--          scans when executing cascade cleanup routines on old event histories,
--          directly reducing system iowait bottlenecks.
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_frame_event_id 
ON frame(event_id);
