/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.service;

import com.codeferm.alarmbian.dao.FrameDao;
import com.codeferm.alarmbian.entity.Frame;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Frame service.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
public class FrameService {

    @Autowired
    private FrameDao frameDao;
    
    /**
     * Create Frame if identity is null. Identity is generated.
     *
     * @param entity Frame entity.
     * @return Frame entity.
     */
    //@Transactional
    public Frame create(@Valid final Frame entity) {
        Assert.isNull(entity.getId(), "Identity field must be null");
        return frameDao.save(entity);
    }
    
    /**
     * Read Frame entity if identity is not null.
     *
     * @param id Entity identity.
     * @return Frame entity.
     */
    public Optional<Frame> read(final Long id) {
        Assert.notNull(id, "Identity field must not be null");
        return frameDao.findById(id);
    }
    
    /**
     * Wrapper method that takes entity.
     *
     * @param entity Frame entity.
     * @return Frame entity.
     */
    public Optional<Frame> read(final Frame entity) {
        return read(entity.getId());
    }
    
    /**
     * Read list of Frame entities.
     *
     * @param ids List of identities.
     * @return List of Frame.
     */
    public List<Frame> read(final Iterable<Long> ids) {
        return frameDao.findAllById(ids);
    }

    /**
     * Update Frame if identity is not null.
     *
     * @param entity Frame entity.
     * @return Frame entity.
     */
    //@Transactional
    public Frame update(@Valid final Frame entity) {
        Assert.notNull(entity.getId(), "Identity field must not be null");
        return frameDao.save(entity);
    }
    
    /**
     * Delete event if identity is not null.
     *
     * @param id Entity identity.
     */
    //@Transactional
    public void delete(final Long id) {
        Assert.notNull(id, "Identity field must not be null");
        frameDao.deleteById(id);
    }

    /**
     * Wrapper method that takes entity.
     *
     * @param entity Frame entity.
     */
    public void delete(final Frame entity) {
        delete(entity.getId());
    }

    /**
     * Count records.
     *
     * @return Total records.
     */
    public long count() {
        return frameDao.count();
    }    
}
