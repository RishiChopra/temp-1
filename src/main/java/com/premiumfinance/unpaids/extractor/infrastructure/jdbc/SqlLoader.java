package com.premiumfinance.unpaids.extractor.infrastructure.jdbc;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads SQL statements from files, so every query lives in {@code src/main/resources/sql} where it can
 * be read and reviewed as SQL.
 * <ul>
 *   <li>{@link #named(String)}: the extractor's own statements, e.g. {@code named("daily-feed/claim")};</li>
 *   <li>{@link #load(String)}: any resource location, used for the feed queries configured in YAML
 *       (ops can point one at {@code file:/config/sql/...} without a rebuild).</li>
 * </ul>
 */
@Component
public class SqlLoader {

    private final ResourceLoader resources;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SqlLoader(ResourceLoader resources) {
        this.resources = resources;
    }

    public String named(String name) {
        return load("classpath:sql/" + name + ".sql");
    }

    public String load(String location) {
        return cache.computeIfAbsent(location, this::read);
    }

    private String read(String location) {
        var resource = resources.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL file not found: " + location);
        }
        try {
            return statementFrom(resource.getContentAsString(StandardCharsets.UTF_8), location);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SQL file " + location, e);
        }
    }

    /** JDBC statements must not end with ';' (or SQL*Plus '/'). */
    private static String statementFrom(String text, String location) {
        String sql = text.strip();
        while (sql.endsWith(";") || sql.endsWith("/")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }
        if (sql.isEmpty()) {
            throw new IllegalStateException("SQL file is empty: " + location);
        }
        return sql;
    }
}
