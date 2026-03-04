package com.joblens.api.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// ShedLock JDBC 설정
@Configuration
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS shedlock (" +
                            "name VARCHAR(64) NOT NULL, " +
                            "lock_until TIMESTAMP(3) NOT NULL, " +
                            "locked_at TIMESTAMP(3) NOT NULL, " +
                            "locked_by VARCHAR(255) NOT NULL, " +
                            "PRIMARY KEY (name))");
        } catch (Exception ignored) {
            // 이미 존재하거나 DB 권한 문제 시 무시
        }
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }
}
