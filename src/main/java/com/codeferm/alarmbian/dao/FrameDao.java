/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.dao;

import com.codeferm.alarmbian.entity.Frame;
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
public interface FrameDao extends ListCrudRepository<Frame, Long> {

}
