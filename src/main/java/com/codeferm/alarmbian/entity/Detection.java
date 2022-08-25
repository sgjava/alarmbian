/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

/**
 * Detection entity.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class Detection {

    /**
     * Database generates primary key.
     */
    @Id
    @Setter(AccessLevel.NONE)
    private Long id;
    @NotNull
    private Long frameId;
    @Column("LABEL_")
    @Size(min = 1, max = 50)
    @NotBlank(message = "label is required")
    private String label;
    @NotNull
    private double confidence;
    @NotNull
    private int yMin;
    @NotNull
    private int xMin;
    @NotNull
    private int yMax;
    @NotNull
    private int xMax;

    /**
     * Writable fields constructor.
     *
     * @param frameId Frame ID.
     * @param label Label.
     * @param confidence Confidence.
     * @param yMin Y min.
     * @param xMin X min.
     * @param yMax Y max.
     * @param xMax X Max.
     */
    public Detection(final Long frameId, final String label, final double confidence, final int yMin, final int xMin, final int yMax,
            final int xMax) {
        this.frameId = frameId;
        this.label = label;
        this.confidence = confidence;
        this.yMin = yMin;
        this.xMin = xMin;
        this.yMax = yMax;
        this.xMax = xMax;
    }

}
