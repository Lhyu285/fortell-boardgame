package com.fortell.boardgame.controllers;

import com.fortell.boardgame.models.GameActionRequest;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.security.AuthSupport;
import com.fortell.boardgame.services.RoomService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoomController {
    private final RoomService roomService;
    private final AuthSupport authSupport;

    public RoomController(RoomService roomService, AuthSupport authSupport) {
        this.roomService = roomService;
        this.authSupport = authSupport;
    }

    @PostMapping("/rooms")
    public RoomDtos.RoomView createRoom(@RequestBody RoomDtos.CreateRoomRequest request, HttpSession session) {
        return roomService.createRoom(authSupport.requireUser(session), request);
    }

    @PostMapping("/rooms/join")
    public RoomDtos.RoomView joinRoom(@RequestBody RoomDtos.JoinRoomRequest request, HttpSession session) {
        return roomService.joinRoom(authSupport.requireUser(session), request);
    }

    @GetMapping("/rooms/{gameType}/{roomId}")
    public RoomDtos.RoomView getRoom(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.getRoom(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/seat")
    public RoomDtos.RoomView moveSeat(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @RequestBody RoomDtos.SeatRequest request,
            HttpSession session
    ) {
        return roomService.moveSeat(gameType, roomId, request.seatIndex(), authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/stand")
    public RoomDtos.RoomView standUp(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.standUp(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/seats/{seatIndex}/stand")
    public RoomDtos.RoomView forceStandUp(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @PathVariable Integer seatIndex,
            HttpSession session
    ) {
        return roomService.forceStandUp(gameType, roomId, seatIndex, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/bots/add")
    public RoomDtos.RoomView addBot(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @RequestBody(required = false) RoomDtos.BotSeatRequest request,
            HttpSession session
    ) {
        return roomService.addBot(gameType, roomId, request == null ? null : request.seatIndex(), authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/bots/remove")
    public RoomDtos.RoomView removeBot(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @RequestBody RoomDtos.BotSeatRequest request,
            HttpSession session
    ) {
        return roomService.removeBot(gameType, roomId, request.seatIndex(), authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/config")
    public RoomDtos.RoomView updateConfig(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @RequestBody RoomDtos.ConfigRequest request,
            HttpSession session
    ) {
        return roomService.updateConfig(gameType, roomId, request.config(), authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/start")
    public RoomDtos.RoomView startGame(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.startGame(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/propose-end")
    public RoomDtos.RoomView proposeEndGame(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.proposeEndGame(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/end")
    public RoomDtos.RoomView endGame(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.endGame(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/leave")
    public RoomDtos.ApiMessage leaveRoom(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.leaveRoom(gameType, roomId, authSupport.requireUser(session));
    }

    @DeleteMapping("/rooms/{gameType}/{roomId}")
    public RoomDtos.ApiMessage dismissRoom(@PathVariable String gameType, @PathVariable String roomId, HttpSession session) {
        return roomService.dismissRoom(gameType, roomId, authSupport.requireUser(session));
    }

    @PostMapping("/rooms/{gameType}/{roomId}/actions")
    public RoomDtos.RoomView gameAction(
            @PathVariable String gameType,
            @PathVariable String roomId,
            @RequestBody GameActionRequest request,
            HttpSession session
    ) {
        return roomService.handleGameAction(
                gameType,
                roomId,
                request.type(),
                request.payload(),
                request.stateVersion(),
                request.clientActionId(),
                authSupport.requireUser(session)
        );
    }
}
