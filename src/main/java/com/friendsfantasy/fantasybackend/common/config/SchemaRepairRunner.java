package com.friendsfantasy.fantasybackend.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaRepairRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        relaxColumnIfNeeded("contests", "created_by_user_id", "BIGINT UNSIGNED NULL");
        relaxColumnIfNeeded("contests", "room_id", "BIGINT UNSIGNED NULL");
        relaxColumnIfNeeded("contest_entries", "user_match_team_id", "BIGINT UNSIGNED NULL");
        relaxColumnIfNeeded("wallet_transactions", "created_by_admin_id", "BIGINT UNSIGNED NULL");
        addColumnIfMissing("user_match_team_players", "is_substitute", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("user_match_team_players", "substitute_priority", "INT NULL");
    }

    private void relaxColumnIfNeeded(String tableName, String columnName, String targetDefinition) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet columns = metaData.getColumns(
                    connection.getCatalog(),
                    null,
                    tableName,
                    columnName
            )) {
                if (!columns.next()) {
                    log.debug("Skipping schema repair for {}.{} because the column was not found", tableName, columnName);
                    return;
                }

                int nullable = columns.getInt("NULLABLE");
                if (nullable == DatabaseMetaData.columnNullable) {
                    return;
                }
            }

            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + targetDefinition);
            log.info("Updated column definition for {}.{} to {}", tableName, columnName, targetDefinition);
        } catch (Exception ex) {
            log.warn("Unable to repair schema for {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet columns = metaData.getColumns(
                    connection.getCatalog(),
                    null,
                    tableName,
                    columnName
            )) {
                if (columns.next()) {
                    return;
                }
            }

            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
            log.info("Added missing column {}.{} with definition {}", tableName, columnName, columnDefinition);
        } catch (Exception ex) {
            log.warn("Unable to add missing column {}.{}: {}", tableName, columnName, ex.getMessage());
        }
    }
}
