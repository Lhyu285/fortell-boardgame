package com.fortell.boardgame.repositories;

import com.fortell.boardgame.models.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> rows = jdbcTemplate.query(
                "select id, username, password_hash, created_at from users where username = ?",
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        Instant.parse(resultSet.getString("created_at"))
                ),
                username
        );
        return rows.stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        List<UserAccount> rows = jdbcTemplate.query(
                "select id, username, password_hash, created_at from users where id = ?",
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        Instant.parse(resultSet.getString("created_at"))
                ),
                id
        );
        return rows.stream().findFirst();
    }

    public UserAccount create(String username, String passwordHash) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into users (username, password_hash, created_at) values (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, now.toString());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? -1 : key.longValue();
        return new UserAccount(id, username, passwordHash, now);
    }
}
