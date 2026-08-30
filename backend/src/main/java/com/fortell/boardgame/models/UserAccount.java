package com.fortell.boardgame.models;

import java.time.Instant;

public record UserAccount(long id, String username, String passwordHash, Instant createdAt) {
}
