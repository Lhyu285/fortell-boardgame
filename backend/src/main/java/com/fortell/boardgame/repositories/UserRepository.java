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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureProfileColumns();
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> rows = jdbcTemplate.query(
                "select id, username, account_id, nickname, preferred_color, password_hash, created_at from users where username = ?",
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("account_id"),
                        resultSet.getString("nickname"),
                        resultSet.getString("preferred_color"),
                        resultSet.getString("password_hash"),
                        Instant.parse(resultSet.getString("created_at"))
                ),
                username
        );
        return rows.stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        List<UserAccount> rows = jdbcTemplate.query(
                "select id, username, account_id, nickname, preferred_color, password_hash, created_at from users where id = ?",
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("account_id"),
                        resultSet.getString("nickname"),
                        resultSet.getString("preferred_color"),
                        resultSet.getString("password_hash"),
                        Instant.parse(resultSet.getString("created_at"))
                ),
                id
        );
        return rows.stream().findFirst();
    }

    public UserAccount create(String username, String passwordHash) {
        Instant now = Instant.now();
        String accountId = newAccountId();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into users (username, password_hash, account_id, nickname, preferred_color, created_at) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, accountId);
            statement.setString(4, username);
            statement.setString(5, "红");
            statement.setString(6, now.toString());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? -1 : key.longValue();
        return new UserAccount(id, username, accountId, username, "红", passwordHash, now);
    }

    public void updateProfile(long userId, String nickname, String preferredColor) {
        jdbcTemplate.update(
                "update users set nickname = ?, preferred_color = ? where id = ?",
                nickname,
                preferredColor,
                userId
        );
        jdbcTemplate.update("update room_members set username = ? where user_id = ?", nickname, userId);
        jdbcTemplate.update("update room_seats set username = ? where user_id = ?", nickname, userId);
    }

    public void updatePassword(long userId, String passwordHash) {
        jdbcTemplate.update("update users set password_hash = ? where id = ?", passwordHash, userId);
    }

    public void deleteById(long userId) {
        jdbcTemplate.update("delete from room_members where user_id = ?", userId);
        jdbcTemplate.update("delete from room_seats where user_id = ?", userId);
        jdbcTemplate.update("delete from users where id = ?", userId);
    }

    private void ensureProfileColumns() {
        Set<String> columns = jdbcTemplate.queryForList("pragma table_info(users)")
                .stream()
                .map(row -> String.valueOf(row.get("name")))
                .collect(Collectors.toSet());
        if (!columns.contains("account_id")) {
            jdbcTemplate.execute("alter table users add column account_id TEXT");
        }
        if (!columns.contains("nickname")) {
            jdbcTemplate.execute("alter table users add column nickname TEXT");
        }
        if (!columns.contains("preferred_color")) {
            jdbcTemplate.execute("alter table users add column preferred_color TEXT");
        }

        jdbcTemplate.queryForList("select id, username from users where account_id is null or trim(account_id) = ''")
                .forEach(row -> jdbcTemplate.update(
                        "update users set account_id = ?, nickname = coalesce(nullif(trim(nickname), ''), ?), preferred_color = coalesce(nullif(trim(preferred_color), ''), '红') where id = ?",
                        newAccountId(),
                        row.get("username"),
                        row.get("id")
                ));
        jdbcTemplate.update("update users set nickname = username where nickname is null or trim(nickname) = ''");
        jdbcTemplate.update("update users set preferred_color = '红' where preferred_color is null or trim(preferred_color) = ''");
        jdbcTemplate.execute("create unique index if not exists idx_users_account_id on users(account_id)");
    }

    private String newAccountId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
