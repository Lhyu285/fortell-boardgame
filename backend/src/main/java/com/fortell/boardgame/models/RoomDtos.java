package com.fortell.boardgame.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record CreateRoomRequest(
            String gameType,
            String roomId,
            String password,
            Integer seatCount,
            Map<String, Object> config
    ) {
    }

    public record JoinRoomRequest(String gameType, String roomId, String password) {
    }

    public record SeatRequest(Integer seatIndex) {
    }

    public record BotSeatRequest(Integer seatIndex) {
    }

    public record ConfigRequest(Map<String, Object> config) {
    }

    public record ApiMessage(boolean success, String message) {
    }

    public record ActionEnvelope(String type, Map<String, Object> payload) {
    }

    public record RoomView(
            String gameType,
            String roomId,
            String status,
            UserSummary owner,
            UserSummary currentUser,
            int seatCount,
            RoomStateView roomState,
            List<SeatView> seats,
            Map<String, Object> config,
            Map<String, Object> gameState,
            List<String> notices,
            List<String> rules,
            ActionPermissions permissions
    ) {
    }

    public record SeatView(
            @JsonProperty("seatIndex")
            int seatIndex,
            @JsonProperty("isOccupied")
            boolean occupied,
            @JsonProperty("user")
            UserSummary user,
            @JsonProperty("ownerSeat")
            boolean ownerSeat,
            @JsonProperty("currentUserSeat")
            boolean currentUserSeat,
            @JsonProperty("botSeat")
            boolean botSeat
    ) {
    }

    public record RoomStateView(
            @JsonProperty("seatCount")
            int seatCount,
            @JsonProperty("occupiedSeatCount")
            int occupiedSeatCount,
            @JsonProperty("emptySeatCount")
            int emptySeatCount,
            @JsonProperty("allSeatsOccupied")
            boolean allSeatsOccupied,
            @JsonProperty("seats")
            List<SeatView> seats
    ) {
    }

    public record ActionPermissions(
            @JsonProperty("canJoinSeat")
            boolean canJoinSeat,
            @JsonProperty("canLeaveRoom")
            boolean canLeaveRoom,
            @JsonProperty("canUpdateConfig")
            boolean canUpdateConfig,
            @JsonProperty("canStartGame")
            boolean canStartGame,
            @JsonProperty("canDismissRoom")
            boolean canDismissRoom,
            @JsonProperty("canStandUp")
            boolean canStandUp,
            @JsonProperty("canSubmitGameAction")
            boolean canSubmitGameAction,
            @JsonProperty("canManageBots")
            boolean canManageBots
    ) {
    }
}
