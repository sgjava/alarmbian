/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.service;

import com.codeferm.alarmbian.dao.EventDao;
import com.codeferm.alarmbian.entity.Event;
import jakarta.validation.Valid;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Event service.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
public class EventService {

    @Autowired
    private EventDao eventDao;

    /**
     * Create Event if identity is null. Identity is generated.
     *
     * @param entity Event entity.
     * @return Event entity.
     */
    @Transactional
    public Event create(@Valid final Event entity) {
        Assert.isNull(entity.getId(), "Identity field must be null");
        return eventDao.save(entity);
    }

    /**
     * Read Event entity if identity is not null.
     *
     * @param id Entity identity.
     * @return Event entity.
     */
    public Optional<Event> read(final Long id) {
        Assert.notNull(id, "Identity field must not be null");
        return eventDao.findById(id);
    }

    /**
     * Wrapper method that takes entity.
     *
     * @param entity Event entity.
     * @return Event entity.
     */
    public Optional<Event> read(final Event entity) {
        return read(entity.getId());
    }

    /**
     * Read list of Event entities.
     *
     * @param ids List of identities.
     * @return List of Event.
     */
    public List<Event> read(final Iterable<Long> ids) {
        return eventDao.findAllById(ids);
    }

    /**
     * Update Event if identity is not null.
     *
     * @param entity Event entity.
     * @return Event entity.
     */
    @Transactional
    public Event update(@Valid final Event entity) {
        Assert.notNull(entity.getId(), "Identity field must not be null");
        return eventDao.save(entity);
    }

    /**
     * Delete event if identity is not null.
     *
     * @param id Entity identity.
     */
    @Transactional
    public void delete(final Long id) {
        Assert.notNull(id, "Identity field must not be null");
        eventDao.deleteById(id);
    }

    /**
     * Wrapper method that takes entity.
     *
     * @param entity Event entity.
     */
    public void delete(final Event entity) {
        delete(entity.getId());
    }

    /**
     * Count records.
     *
     * @return Total records.
     */
    public long count() {
        return eventDao.count();
    }

    /**
     * Find events by device name and timestamp.
     *
     * @param deviceName Device name.
     * @param timestamp Timestamp.
     * @return List of Event.
     */
    public List<Event> findByTime(final String deviceName, final Timestamp timestamp) {
        return eventDao.findByTime(deviceName, timestamp);
    }

    /**
     * Delete events by device name and timestamp.
     *
     * @param deviceName Device name.
     * @param timestamp Timestamp.
     * @return Records deleted.
     */
    public int deleteByTime(final String deviceName, final Timestamp timestamp) {
        return eventDao.deleteByTime(deviceName, timestamp);
    }
}
