/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.dao;

import com.codeferm.alarmbian.entity.Event;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Crud repository for event table.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
public interface EventDao extends ListCrudRepository<Event, Long> {

    /**
     * Get all entities by device name.
     *
     * @param deviceName Device name.
     * @param timestamp Timestamp.
     * @return List of Event entities.
     */
    @Query(value
            = "select ID, DEVICE_NAME, EVENT_TYPE, EVENT_DATA, EVENT_TIME from EVENT where DEVICE_NAME = :deviceName and EVENT_TYPE in ('RECORD_START',  'HISTORY_STOP') and EVENT_TIME <= :timestamp  order by ID")
    List<Event> findByTime(final String deviceName, final Timestamp timestamp);

    /**
     * Delete all records by name and timestamp except 'START_UP' and 'SHUT_DOWN'.
     *
     * @param deviceName Device name.
     * @param timestamp Timestamp.
     * @return Records deleted.
     */
    @Modifying
    @Query(value
            = "delete from EVENT where DEVICE_NAME = :deviceName and EVENT_TYPE not in ('START_UP',  'SHUT_DOWN') and EVENT_TIME <= :timestamp")
    int deleteByTime(final String deviceName, final Timestamp timestamp);
}
