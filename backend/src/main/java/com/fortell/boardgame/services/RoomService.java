package com.fortell.boardgame.services;

import com.fortell.boardgame.game_modules.GameModule;
import com.fortell.boardgame.game_modules.GameModuleRegistry;
import com.fortell.boardgame.game_modules.VersionedGameModule;
import com.fortell.boardgame.models.ApiException;
import com.fortell.boardgame.models.GameType;
import com.fortell.boardgame.models.RoomDtos;
import com.fortell.boardgame.models.RoomEntity;
import com.fortell.boardgame.models.RoomMember;
import com.fortell.boardgame.models.RoomSeat;
import com.fortell.boardgame.models.RoomStatus;
import com.fortell.boardgame.models.UserSummary;
import com.fortell.boardgame.repositories.RoomRepository;
import com.fortell.boardgame.repositories.UserRepository;
import com.fortell.boardgame.utils.JsonUtils;
import com.fortell.boardgame.utils.RoomIdGenerator;
import com.fortell.boardgame.utils.RoomRules;
import com.fortell.boardgame.websocket.RealtimeGateway;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameModuleRegistry gameModuleRegistry;
    private final JsonUtils jsonUtils;
    private final PasswordEncoder passwordEncoder;
    private final RealtimeGateway realtimeGateway;
    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();

    public RoomService(RoomRepository roomRepository, UserRepository userRepository, GameModuleRegistry gameModuleRegistry,
                       JsonUtils jsonUtils, PasswordEncoder passwordEncoder, RealtimeGateway realtimeGateway) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.gameModuleRegistry = gameModuleRegistry;
        this.jsonUtils = jsonUtils;
        this.passwordEncoder = passwordEncoder;
        this.realtimeGateway = realtimeGateway;
    }

    @PreDestroy
    public void destroyAllRoomsOnShutdown() {
        roomRepository.deleteAllRooms();
        roomLocks.clear();
    }

    public RoomDtos.RoomView createRoom(UserSummary user, RoomDtos.CreateRoomRequest request) {
        synchronized (this) {
            GameType gameType = parseGameType(request.gameType());
            GameModule module = gameModuleRegistry.get(gameType);
            int seatCount = normalizeSeatCount(request.seatCount(), gameType);
            String rawRoomId = request.roomId() == null ? "" : request.roomId().trim().toLowerCase();
            String roomId = rawRoomId.isBlank() ? generateUniqueRoomId(gameType) : rawRoomId;
            if (!RoomRules.isValidRoomId(roomId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "房间号不合法");
            }
            if (!RoomRules.isValidRoomPassword(request.password())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "房间密码不合法");
            }
            if (roomRepository.findByGameTypeAndRoomId(gameType, roomId).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "房间号已存在");
            }

            Instant now = Instant.now();
            List<RoomSeat> seats = emptySeats(seatCount);
            String passwordHash = encodePassword(request.password());
            RoomEntity draft = new RoomEntity(
                    0, gameType, roomId, user.id(), passwordHash, seatCount,
                    RoomStatus.WAITING, "{}", "{}", now, now
            );
            Map<String, Object> config = module.sanitizeConfig(request.config(), draft, seats);
            Map<String, Object> state = module.initialState(draft, seats);

            RoomEntity created = roomRepository.create(new RoomEntity(
                    0,
                    gameType,
                    roomId,
                    user.id(),
                    passwordHash,
                    seatCount,
                    RoomStatus.WAITING,
                    jsonUtils.write(config),
                    jsonUtils.write(state),
                    now,
                    now
            ));
            roomRepository.addMember(created.id(), user);
            roomRepository.occupySeat(created.id(), 0, user);

            LoadedRoom loadedRoom = load(gameType, roomId, "房间不存在");
            broadcastRoom(loadedRoom);
            return toView(loadedRoom, user);
        }
    }

    public RoomDtos.RoomView joinRoom(UserSummary user, RoomDtos.JoinRoomRequest request) {
        GameType gameType = parseGameType(request.gameType());
        String roomId = normalizeRoomId(request.roomId());
        if (!RoomRules.isValidRoomId(roomId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "房间号或密码错误");
        }
        if (!RoomRules.isValidRoomPassword(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "房间密码不合法");
        }

        synchronized (lockFor(gameType, roomId)) {
            LoadedRoom loadedRoom = load(gameType, roomId, "房间号或密码错误");
            if (loadedRoom.entity.hasPassword()) {
                String password = request.password() == null ? "" : request.password();
                if (!passwordEncoder.matches(password, loadedRoom.entity.passwordHash())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "房间号或密码错误");
                }
            }

            roomRepository.addMember(loadedRoom.entity.id(), user);

            LoadedRoom updatedRoom = load(gameType, roomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView getRoom(String gameTypePath, String roomId, UserSummary currentUser) {
        LoadedRoom loadedRoom = load(parseGameType(gameTypePath), normalizeRoomId(roomId), "房间不存在");
        ensureMember(loadedRoom, currentUser);
        return toView(loadedRoom, currentUser);
    }

    public RoomDtos.RoomView moveSeat(String gameTypePath, String roomId, Integer seatIndex, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureMember(loadedRoom, user);
            ensureWaiting(loadedRoom.entity);
            if (seatIndex == null || seatIndex < 0 || seatIndex >= loadedRoom.entity.seatCount()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "座位不存在");
            }

            RoomSeat targetSeat = loadedRoom.seats.get(seatIndex);
            if (targetSeat.occupied() && !Objects.equals(targetSeat.userId(), user.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "该座位已被占用");
            }

            roomRepository.clearSeatByUser(loadedRoom.entity.id(), user.id());
            roomRepository.occupySeat(loadedRoom.entity.id(), seatIndex, user);

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView standUp(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureMember(loadedRoom, user);
            ensureWaiting(loadedRoom.entity);
            if (seatOf(loadedRoom.seats, user.id()).isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "你当前未入座");
            }

            roomRepository.clearSeatByUser(loadedRoom.entity.id(), user.id());
            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView forceStandUp(String gameTypePath, String roomId, Integer seatIndex, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureOwner(loadedRoom.entity, user);
            ensureWaiting(loadedRoom.entity);
            if (seatIndex == null || seatIndex < 0 || seatIndex >= loadedRoom.entity.seatCount()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "座位不存在");
            }

            RoomSeat targetSeat = loadedRoom.seats.get(seatIndex);
            if (!targetSeat.occupied()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "该座位为空");
            }

            roomRepository.clearSeat(loadedRoom.entity.id(), seatIndex);
            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView addBot(String gameTypePath, String roomId, Integer seatIndex, UserSummary user) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "当前版本未启用机器人功能");
    }

    public RoomDtos.RoomView removeBot(String gameTypePath, String roomId, Integer seatIndex, UserSummary user) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "当前版本未启用机器人功能");
    }

    public RoomDtos.RoomView updateConfig(String gameTypePath, String roomId, Map<String, Object> requestedConfig, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureOwner(loadedRoom.entity, user);
            if (loadedRoom.entity.status() != RoomStatus.WAITING) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "游戏进行中无法修改配置");
            }

            Map<String, Object> input = new LinkedHashMap<>(requestedConfig == null ? Map.of() : requestedConfig);
            Integer requestedSeats = input.containsKey("seatCount") ? asInteger(input.remove("seatCount")) : null;
            int nextSeatCount = normalizeSeatCount(
                    requestedSeats == null ? loadedRoom.entity.seatCount() : requestedSeats,
                    gameType
            );
            List<RoomSeat> nextSeats = resizeSeatList(loadedRoom.seats, nextSeatCount);

            RoomEntity roomForValidation = new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    nextSeatCount,
                    loadedRoom.entity.status(),
                    loadedRoom.entity.configJson(),
                    loadedRoom.entity.gameStateJson(),
                    loadedRoom.entity.createdAt(),
                    Instant.now()
            );
            GameModule module = gameModuleRegistry.get(gameType);
            Map<String, Object> config = module.sanitizeConfig(input, roomForValidation, loadedRoom.seats);

            if (nextSeatCount != loadedRoom.entity.seatCount()) {
                roomRepository.replaceSeats(loadedRoom.entity.id(), nextSeats);
            }

            roomRepository.update(new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    nextSeatCount,
                    loadedRoom.entity.status(),
                    jsonUtils.write(config),
                    loadedRoom.entity.gameStateJson(),
                    loadedRoom.entity.createdAt(),
                    Instant.now()
            ));

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView startGame(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureOwner(loadedRoom.entity, user);

            if (humanSeatCount(loadedRoom.seats) != loadedRoom.entity.seatCount()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "房间内仍有空座位，无法开始游戏");
            }

            GameModule module = gameModuleRegistry.get(gameType);
            module.validateCanStart(loadedRoom.entity, loadedRoom.seats);
            Map<String, Object> state = module.onStart(loadedRoom.entity, loadedRoom.seats, loadedRoom.config);
            Instant updatedAt = Instant.now();
            String stateJson = jsonUtils.write(state);

            roomRepository.update(new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    loadedRoom.entity.seatCount(),
                    RoomStatus.IN_PROGRESS,
                    loadedRoom.entity.configJson(),
                    stateJson,
                    loadedRoom.entity.createdAt(),
                    updatedAt
            ));
            if (module instanceof VersionedGameModule) {
                roomRepository.insertSnapshot(
                        loadedRoom.entity.id(),
                        currentStateVersion(state),
                        stateJson,
                        updatedAt
                );
            }

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView proposeEndGame(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureMember(loadedRoom, user);
            if (loadedRoom.entity.status() != RoomStatus.IN_PROGRESS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "当前房间尚未开始游戏");
            }
            if (loadedRoom.entity.ownerUserId() == user.id()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "房主可以直接结束游戏");
            }

            if (isGameFinished(loadedRoom.state)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "\u6e38\u620f\u5df2\u7ecf\u7ed3\u675f\uff0c\u4e0d\u80fd\u63d0\u8bae\u7ed3\u675f\u6e38\u620f"
                );
            }
            if (seatOf(loadedRoom.seats, user.id()).isEmpty()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "\u89c2\u6218\u73a9\u5bb6\u4e0d\u80fd\u63d0\u8bae\u7ed3\u675f\u6e38\u620f"
                );
            }

            Map<String, Object> nextState = new LinkedHashMap<>(loadedRoom.state);
            List<Map<String, Object>> proposals = endProposalsOf(nextState);
            boolean alreadyProposed = proposals.stream()
                    .anyMatch(proposal -> Objects.equals(asLong(proposal.get("userId")), user.id()));
            if (alreadyProposed) {
                proposals.removeIf(proposal -> Objects.equals(asLong(proposal.get("userId")), user.id()));
            } else {
                proposals.add(new LinkedHashMap<>(Map.of(
                        "userId", user.id(),
                        "username", user.username()
                )));
            }
            nextState.put("endProposals", proposals);
            if (proposals.isEmpty()) {
                nextState.remove("endProposalText");
            } else {
                String proposalText = endProposalText(proposals);
                nextState.put("endProposalText", proposalText);
                if (!alreadyProposed) {
                    prependNotice(nextState, proposalText);
                }
            }

            roomRepository.update(new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    loadedRoom.entity.seatCount(),
                    loadedRoom.entity.status(),
                    loadedRoom.entity.configJson(),
                    jsonUtils.write(nextState),
                    loadedRoom.entity.createdAt(),
                    Instant.now()
            ));

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.RoomView endGame(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureOwner(loadedRoom.entity, user);
            if (loadedRoom.entity.status() != RoomStatus.IN_PROGRESS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "当前房间尚未开始游戏");
            }

            GameModule module = gameModuleRegistry.get(gameType);
            Map<String, Object> nextState = module.initialState(loadedRoom.entity, loadedRoom.seats);
            prependNotice(nextState, "房主已结束游戏，房间回到准备界面。");

            roomRepository.update(new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    loadedRoom.entity.seatCount(),
                    RoomStatus.WAITING,
                    loadedRoom.entity.configJson(),
                    jsonUtils.write(nextState),
                    loadedRoom.entity.createdAt(),
                    Instant.now()
            ));

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    public RoomDtos.ApiMessage dismissRoom(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureOwner(loadedRoom.entity, user);
            roomRepository.delete(loadedRoom.entity.id());
            realtimeGateway.broadcastDismissed(gameType.path(), normalizedRoomId, "房间已被房主解散");
            return new RoomDtos.ApiMessage(true, "房间已解散");
        }
    }

    public RoomDtos.ApiMessage leaveRoom(String gameTypePath, String roomId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureMember(loadedRoom, user);
            boolean seated = seatOf(loadedRoom.seats, user.id()).isPresent();
            if (!canLeaveRoom(true, seated, loadedRoom.entity.status(), loadedRoom.state)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "\u6e38\u620f\u8fdb\u884c\u4e2d\u7684\u5165\u5ea7\u73a9\u5bb6\u4e0d\u80fd\u9000\u51fa\u623f\u95f4"
                );
            }

            if (loadedRoom.entity.ownerUserId() == user.id()) {
                roomRepository.delete(loadedRoom.entity.id());
                realtimeGateway.broadcastDismissed(gameType.path(), normalizedRoomId, "房主已退出，房间已解散");
                return new RoomDtos.ApiMessage(true, "房间已解散");
            }

            roomRepository.clearSeatByUser(loadedRoom.entity.id(), user.id());
            roomRepository.removeMember(loadedRoom.entity.id(), user.id());

            LoadedRoom afterLeave = load(gameType, normalizedRoomId, "房间不存在");
            if (afterLeave.members.isEmpty()) {
                roomRepository.delete(afterLeave.entity.id());
                realtimeGateway.broadcastDismissed(gameType.path(), normalizedRoomId, "房间已关闭");
                return new RoomDtos.ApiMessage(true, "已退出房间");
            }
            Map<String, Object> nextState = new LinkedHashMap<>(afterLeave.state);
            prependNotice(nextState, user.username() + " 已退出房间。");

            RoomStatus nextStatus = afterLeave.entity.status();
            if (humanSeatCount(afterLeave.seats) < gameType.minPlayers() && afterLeave.entity.status() == RoomStatus.IN_PROGRESS) {
                nextStatus = RoomStatus.WAITING;
                prependNotice(nextState, "当前入座人数不足，房间已回到等待状态。");
            }

            roomRepository.update(new RoomEntity(
                    afterLeave.entity.id(),
                    afterLeave.entity.gameType(),
                    afterLeave.entity.roomId(),
                    afterLeave.entity.ownerUserId(),
                    afterLeave.entity.passwordHash(),
                    afterLeave.entity.seatCount(),
                    nextStatus,
                    afterLeave.entity.configJson(),
                    jsonUtils.write(nextState),
                    afterLeave.entity.createdAt(),
                    Instant.now()
            ));

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return new RoomDtos.ApiMessage(true, "已退出房间");
        }
    }

    public RoomDtos.RoomView handleGameAction(String gameTypePath, String roomId, String actionType,
                                              Map<String, Object> payload, Long stateVersion,
                                              String clientActionId, UserSummary user) {
        GameType gameType = parseGameType(gameTypePath);
        String normalizedRoomId = normalizeRoomId(roomId);
        synchronized (lockFor(gameType, normalizedRoomId)) {
            LoadedRoom loadedRoom = load(gameType, normalizedRoomId, "房间不存在");
            ensureMember(loadedRoom, user);
            if (loadedRoom.entity.status() != RoomStatus.IN_PROGRESS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "当前房间尚未开始游戏");
            }

            GameModule module = gameModuleRegistry.get(gameType);
            boolean versioned = module instanceof VersionedGameModule;
            Map<String, Object> actionPayload = payload == null ? Map.of() : payload;
            if (versioned) {
                validateVersionedAction(loadedRoom, user, stateVersion, clientActionId);
            }
            Map<String, Object> state = module.onAction(
                    loadedRoom.entity,
                    loadedRoom.seats,
                    loadedRoom.config,
                    new LinkedHashMap<>(loadedRoom.state),
                    user,
                    actionType,
                    actionPayload,
                    noticesOf(loadedRoom.state)
            );
            if (versioned) {
                state.put("version", currentStateVersion(loadedRoom.state) + 1);
            }
            RoomStatus nextStatus = state.containsKey("_roomStatus")
                    ? RoomStatus.valueOf(String.valueOf(state.remove("_roomStatus")))
                    : loadedRoom.entity.status();

            Instant updatedAt = Instant.now();
            String stateJson = jsonUtils.write(state);
            roomRepository.update(new RoomEntity(
                    loadedRoom.entity.id(),
                    loadedRoom.entity.gameType(),
                    loadedRoom.entity.roomId(),
                    loadedRoom.entity.ownerUserId(),
                    loadedRoom.entity.passwordHash(),
                    loadedRoom.entity.seatCount(),
                    nextStatus,
                    loadedRoom.entity.configJson(),
                    stateJson,
                    loadedRoom.entity.createdAt(),
                    updatedAt
            ));
            if (versioned) {
                long nextVersion = currentStateVersion(state);
                roomRepository.insertClientAction(
                        loadedRoom.entity.id(),
                        user.id(),
                        clientActionId.trim(),
                        stateVersion,
                        updatedAt
                );
                roomRepository.insertSnapshot(
                        loadedRoom.entity.id(),
                        nextVersion,
                        stateJson,
                        updatedAt
                );
                roomRepository.insertActionLog(
                        loadedRoom.entity.id(),
                        nextVersion,
                        user.id(),
                        actionType,
                        jsonUtils.write(new LinkedHashMap<>(actionPayload)),
                        jsonUtils.write(lastActionEvent(state)),
                        lastActionMessage(state, actionType),
                        updatedAt
                );
            }

            LoadedRoom updatedRoom = load(gameType, normalizedRoomId, "房间不存在");
            broadcastRoom(updatedRoom);
            return toView(updatedRoom, user);
        }
    }

    private LoadedRoom load(GameType gameType, String roomId, String missingMessage) {
        RoomEntity entity = roomRepository.findByGameTypeAndRoomId(gameType, roomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, missingMessage));
        sanitizeLegacySeats(entity.id());
        List<RoomSeat> seats = roomRepository.listSeats(entity.id());
        backfillMembers(entity, seats);
        List<RoomMember> members = roomRepository.listMembers(entity.id());
        return new LoadedRoom(
                entity,
                seats,
                members,
                jsonUtils.readMap(entity.configJson()),
                jsonUtils.readMap(entity.gameStateJson())
        );
    }

    private void sanitizeLegacySeats(long roomDatabaseId) {
        roomRepository.clearLegacyBotSeats(roomDatabaseId);
    }

    private void backfillMembers(RoomEntity entity, List<RoomSeat> seats) {
        userRepository.findById(entity.ownerUserId())
                .map(account -> new UserSummary(account.id(), account.username()))
                .ifPresent(user -> roomRepository.addMember(entity.id(), user));
        for (RoomSeat seat : seats) {
            if (!seat.occupied() || seat.bot()) {
                continue;
            }
            roomRepository.addMember(entity.id(), new UserSummary(seat.userId(), seat.username()));
        }
    }

    private RoomDtos.RoomView toView(LoadedRoom loadedRoom, UserSummary currentUser) {
        GameModule module = gameModuleRegistry.get(loadedRoom.entity.gameType());
        UserSummary owner = userRepository.findById(loadedRoom.entity.ownerUserId())
                .map(account -> new UserSummary(account.id(), account.username()))
                .orElseGet(() -> new UserSummary(loadedRoom.entity.ownerUserId(), "房主"));
        boolean member = memberOf(loadedRoom.members, currentUser.id());
        boolean seated = seatOf(loadedRoom.seats, currentUser.id()).isPresent();
        boolean ownerUser = currentUser.id() == loadedRoom.entity.ownerUserId();
        List<RoomDtos.SeatView> seatViews = loadedRoom.seats.stream()
                .map(seat -> toSeatView(seat, loadedRoom.entity, currentUser))
                .toList();
        int occupiedSeatCount = humanSeatCount(loadedRoom.seats);
        RoomDtos.RoomStateView roomState = new RoomDtos.RoomStateView(
                loadedRoom.entity.seatCount(),
                occupiedSeatCount,
                loadedRoom.entity.seatCount() - occupiedSeatCount,
                occupiedSeatCount == loadedRoom.entity.seatCount(),
                seatViews
        );

        return new RoomDtos.RoomView(
                loadedRoom.entity.gameType().path(),
                loadedRoom.entity.roomId(),
                loadedRoom.entity.status().name(),
                owner,
                currentUser,
                loadedRoom.entity.seatCount(),
                roomState,
                seatViews,
                loadedRoom.config,
                loadedRoom.state,
                noticesOf(loadedRoom.state),
                module.rules(),
                new RoomDtos.ActionPermissions(
                        canJoinAnyEmptySeat(member, loadedRoom.entity.status(), loadedRoom.seats),
                        canLeaveRoom(member, seated, loadedRoom.entity.status(), loadedRoom.state),
                        ownerUser && loadedRoom.entity.status() == RoomStatus.WAITING,
                        ownerUser && loadedRoom.entity.status() == RoomStatus.WAITING
                                && humanSeatCount(loadedRoom.seats) == loadedRoom.entity.seatCount(),
                        ownerUser,
                        seated && loadedRoom.entity.status() == RoomStatus.WAITING,
                        seated && loadedRoom.entity.status() == RoomStatus.IN_PROGRESS,
                        false
                )
        );
    }

    private RoomDtos.SeatView toSeatView(RoomSeat seat, RoomEntity roomEntity, UserSummary currentUser) {
        boolean occupied = seat.occupied() && seat.username() != null && !seat.username().isBlank();
        return new RoomDtos.SeatView(
                seat.seatIndex(),
                occupied,
                occupied ? new UserSummary(seat.userId(), seat.username()) : null,
                occupied && !seat.bot() && seat.userId() == roomEntity.ownerUserId(),
                occupied && !seat.bot() && seat.userId() == currentUser.id(),
                seat.bot()
        );
    }

    private boolean canJoinAnyEmptySeat(boolean member, RoomStatus status, List<RoomSeat> seats) {
        return member && status == RoomStatus.WAITING && seats.stream().anyMatch(seat -> !seat.occupied());
    }

    private boolean canLeaveRoom(boolean member, boolean seated, RoomStatus status, Map<String, Object> state) {
        return member && (status != RoomStatus.IN_PROGRESS || !seated || isGameFinished(state));
    }

    private boolean isGameFinished(Map<String, Object> state) {
        String phase = Objects.toString(state.get("phase"), "");
        return "finished".equalsIgnoreCase(phase) || "game_over".equalsIgnoreCase(phase);
    }

    private void broadcastRoom(LoadedRoom loadedRoom) {
        realtimeGateway.broadcastRoom(
                loadedRoom.entity.gameType().path(),
                loadedRoom.entity.roomId(),
                viewer -> toView(loadedRoom, viewer)
        );
    }

    private List<String> noticesOf(Map<String, Object> state) {
        return ((List<?>) state.getOrDefault("notices", List.of())).stream().map(String::valueOf).toList();
    }

    private void validateVersionedAction(LoadedRoom loadedRoom, UserSummary user, Long stateVersion,
                                         String clientActionId) {
        if (stateVersion == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "stateVersion is required");
        }
        long currentVersion = currentStateVersion(loadedRoom.state);
        if (stateVersion != currentVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "State version is outdated");
        }
        if (clientActionId == null || clientActionId.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "clientActionId is required");
        }
        if (roomRepository.clientActionExists(loadedRoom.entity.id(), user.id(), clientActionId.trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Duplicate client action");
        }
    }

    private long currentStateVersion(Map<String, Object> state) {
        Object value = state.get("version");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastActionEvent(Map<String, Object> state) {
        List<?> actionLog = (List<?>) state.getOrDefault("actionLog", List.of());
        if (actionLog.isEmpty() || !(actionLog.get(0) instanceof Map<?, ?> event)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) event);
    }

    private String lastActionMessage(Map<String, Object> state, String fallbackActionType) {
        Object summary = lastActionEvent(state).get("summary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            return String.valueOf(summary);
        }
        List<String> notices = noticesOf(state);
        if (!notices.isEmpty()) {
            return notices.get(0);
        }
        return fallbackActionType;
    }

    private void ensureOwner(RoomEntity roomEntity, UserSummary user) {
        if (roomEntity.ownerUserId() != user.id()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有房主可以执行该操作");
        }
    }

    private void ensureMember(LoadedRoom loadedRoom, UserSummary user) {
        if (!memberOf(loadedRoom.members, user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "请先加入房间");
        }
    }

    private void ensureWaiting(RoomEntity roomEntity) {
        if (roomEntity.status() != RoomStatus.WAITING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "游戏进行中无法调整座位");
        }
    }

    private List<RoomSeat> resizeSeatList(List<RoomSeat> seats, int nextSeatCount) {
        int occupiedCount = humanSeatCount(seats);
        if (nextSeatCount < occupiedCount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "房间人数不能少于当前入座人数");
        }
        if (nextSeatCount == seats.size()) {
            return seats;
        }
        if (nextSeatCount > seats.size()) {
            List<RoomSeat> expanded = new ArrayList<>(seats);
            for (int seatIndex = seats.size(); seatIndex < nextSeatCount; seatIndex++) {
                expanded.add(new RoomSeat(seatIndex, null, null));
            }
            return expanded;
        }

        List<RoomSeat> remaining = new ArrayList<>(seats);
        while (remaining.size() > nextSeatCount) {
            for (int index = remaining.size() - 1; index >= 0; index--) {
                if (!remaining.get(index).occupied()) {
                    remaining.remove(index);
                    break;
                }
            }
        }

        List<RoomSeat> renumbered = new ArrayList<>();
        for (int index = 0; index < remaining.size(); index++) {
            RoomSeat seat = remaining.get(index);
            renumbered.add(new RoomSeat(index, seat.userId(), seat.username()));
        }
        return renumbered;
    }

    private GameType parseGameType(String rawGameType) {
        try {
            return GameType.fromPath(rawGameType);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的游戏");
        }
    }

    private String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim().toLowerCase();
    }

    private String generateUniqueRoomId(GameType gameType) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = RoomIdGenerator.generate(6);
            if (roomRepository.findByGameTypeAndRoomId(gameType, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "房间号生成失败，请重试");
    }

    private String encodePassword(String password) {
        return password == null || password.isBlank() ? null : passwordEncoder.encode(password);
    }

    private int normalizeSeatCount(Integer requested, GameType gameType) {
        int seatCount = requested == null ? gameType.defaultSeats() : requested;
        if (seatCount < gameType.minPlayers() || seatCount > gameType.maxPlayers()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "支持人数：" + gameType.minPlayers() + "-" + gameType.maxPlayers() + "人"
            );
        }
        return seatCount;
    }

    private Optional<RoomSeat> seatOf(List<RoomSeat> seats, long userId) {
        return seats.stream()
                .filter(seat -> !seat.bot() && Objects.equals(seat.userId(), userId))
                .findFirst();
    }

    private boolean memberOf(List<RoomMember> members, long userId) {
        return members.stream().anyMatch(member -> member.userId() == userId);
    }

    private int humanSeatCount(List<RoomSeat> seats) {
        return (int) seats.stream().filter(RoomSeat::occupied).filter(seat -> !seat.bot()).count();
    }

    private long determineNextOwner(LoadedRoom loadedRoom, long leavingUserId) {
        if (loadedRoom.entity.ownerUserId() != leavingUserId) {
            return loadedRoom.entity.ownerUserId();
        }

        Optional<Long> seatedMember = loadedRoom.seats.stream()
                .filter(RoomSeat::occupied)
                .filter(seat -> !seat.bot())
                .map(RoomSeat::userId)
                .findFirst();
        if (seatedMember.isPresent()) {
            return seatedMember.get();
        }

        return loadedRoom.members.stream()
                .map(RoomMember::userId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "无法确定新的房主"));
    }

    private List<RoomSeat> emptySeats(int seatCount) {
        List<RoomSeat> seats = new ArrayList<>();
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            seats.add(new RoomSeat(seatIndex, null, null));
        }
        return seats;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> endProposalsOf(Map<String, Object> state) {
        List<Map<String, Object>> proposals = new ArrayList<>();
        for (Object item : (List<?>) state.getOrDefault("endProposals", List.of())) {
            if (item instanceof Map<?, ?> map) {
                proposals.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return proposals;
    }

    private String endProposalText(List<Map<String, Object>> proposals) {
        String names = proposals.stream()
                .map(proposal -> String.valueOf(proposal.get("username")))
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
        return "玩家" + names + "提议结束游戏";
    }

    @SuppressWarnings("unchecked")
    private void prependNotice(Map<String, Object> state, String notice) {
        List<String> notices = new ArrayList<>((List<String>) state.getOrDefault("notices", List.of()));
        notices.add(0, notice);
        state.put("notices", notices.stream().limit(12).toList());
    }

    private Object lockFor(GameType gameType, String roomId) {
        return roomLocks.computeIfAbsent(gameType.path() + ":" + roomId, ignored -> new Object());
    }

    private record LoadedRoom(
            RoomEntity entity,
            List<RoomSeat> seats,
            List<RoomMember> members,
            Map<String, Object> config,
            Map<String, Object> state
    ) {
    }
}
