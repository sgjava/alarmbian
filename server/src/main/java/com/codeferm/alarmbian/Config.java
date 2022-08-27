/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Config bean has common helper methods to assist with property file.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Slf4j
public class Config {

    /**
     * Spring environment.
     */
    @Autowired
    private Environment env;

    /**
     * Initialize bean.
     */
    @PostConstruct
    public void init() {
        log.debug("init");
    }

    /**
     * Clean up.
     */
    @PreDestroy
    public void done() {
        log.debug("done");
    }

    /**
     * Get Map of keys/values using prefix and numeric suffix. Suffix should start with 1 (prefix.1, prefix.2, etc.). If space is
     * found then Map is split into key and value instead of null value.
     *
     * @param prefixKey Lookup key prefix.
     * @param map Map to add keys/values to.
     */
    public void getProperties(final String prefixKey, final Map<String, String> map) {
        var i = 1;
        var key = String.format("%s.%d", prefixKey, i);
        var found = env.containsProperty(key);
        while (found) {
            final var value = env.getProperty(key);
            if (value != null) {
                final var split = value.split(" ");
                if (split.length > 1) {
                    map.put(split[0], split[1]);
                } else {
                    map.put(value, null);
                }
            }
            key = String.format("%s.%d", prefixKey, ++i);
            found = env.containsProperty(key);
        }
    }

    /**
     * Return list of Integer from property in format 1, 2, 3...
     *
     * @param key Property key.
     * @return List of Integer.
     */
    public List<Integer> getList(final String key) {
        var list = new ArrayList<Integer>();
        final var split = env.getProperty(key).split(",");
        for (String string : split) {
            list.add(Integer.parseInt(string.strip()));
        }
        return list;
    }

    /**
     * Return Double value based on key.
     *
     * @param key Property key.
     * @return Double value.
     */
    public Double getDouble(final String key) {
        return Double.parseDouble(env.getProperty(key));
    }
}
