# 技术选型说明

本项目采用前后端分离的 MVP 结构：

- 后端：`Spring Boot 4.0.5`、`Java 21` 基线、`SQLite`、`WebSocket`
- 前端：`React 18`、`Vite`、`React Router`
- 登录态：`Session Cookie`

选择原因：

- Spring Boot + SQLite 足够覆盖注册、登录、房间、实时同步的最小工程闭环。
- Session Cookie 让网页端和 WebSocket 复用同一登录态，复杂度低于 JWT。
- React + Vite 启动快，适合在空目录里快速搭完整前端。
- 五子棋本次降为低优先级模块，只保留建房、占位和基础状态占位，不影响其他模块运行。

# 项目结构

```text
backend/
├── src/main/java/com/fortell/boardgame/
│   ├── config/
│   ├── controllers/
│   ├── game_modules/
│   ├── models/
│   ├── repositories/
│   ├── security/
│   ├── services/
│   ├── utils/
│   └── websocket/
└── src/main/resources/

frontend/
├── src/components/
├── src/hooks/
├── src/lib/
├── src/pages/
└── src/styles/
```

# 运行说明

## 后端

要求：

- `JDK 21+`
- 当前代码按 `Java 21` 编译目标编写，不使用更高版本 API

Windows 示例：

```powershell
cd e:\projects\boardgame\backend
$env:JAVA_HOME='D:\JDK 21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

后端默认地址：

- `http://localhost:8080`

## 前端

```powershell
cd e:\projects\boardgame\frontend
npm install
npm run dev
```

前端默认地址：

- `http://localhost:5173`

# 核心设计说明

## 房间模型设计

- `rooms` 表保存房间主记录：游戏类型、房间号、房主、密码哈希、人数、状态、配置 JSON、游戏状态 JSON。
- `room_seats` 表保存每个座位的占用情况。
- 房间状态以服务端为准，前端只渲染快照。

## 游戏模块解耦方式

- 统一使用 `GameModule` 接口。
- 每个游戏独立提供：
  - `defaultConfig`
  - `initialState`
  - `sanitizeConfig`
  - `validateCanStart`
  - `onStart`
  - `onAction`
- 房间服务不写死某个具体游戏逻辑，通过 `GameModuleRegistry` 按 `gameType` 调用。

## 实时通信实现方式

- REST 用于注册、登录、建房、入房、改配置、开始、退出、解散。
- WebSocket 用于房间实时快照广播。
- 房间内任何关键操作完成后，服务端广播最新 `room.snapshot`。

# 验证说明

建议按以下路径手工验证：

1. 注册 / 登录
   - 打开前端登录页
   - 切换到注册
   - 输入用户名、密码、确认密码、验证码
   - 成功后应进入大厅

2. 创建房间
   - 在大厅进入 `rps` 或 `brass`
   - 输入合法房间号或留空自动生成
   - 创建成功后应跳转到 `/{game}/{roomId}`

3. 加入房间
   - 用另一个浏览器窗口登录
   - 输入相同房间号加入
   - 成功后两边都应看到座位同步

4. 游戏流程
   - `rps`：房主开始，玩家选择出拳并确认，公告栏显示结果
   - `brass`：房主开始后显示占位态提示
   - `gobang`：本版不作为完整玩法交付，只验证建房、入房和基础状态展示


# 启动说明

后端：

cd e:\projects\boardgame\backend
$env:JAVA_HOME='D:\JDK 21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run

http://localhost:8080

前端：

cd e:\projects\boardgame\frontend
npm install
npm run dev

http://localhost:5173


# 性能与扩展分析

## 并发策略

- 单机单实例
- 房间粒度加锁，避免同一房间并发写入时状态错乱
- 广播以房间为单位分发 WebSocket 消息

## 房间数量估算

- 当前实现适合 MVP、小规模内部测试或演示环境
- 房间配置和状态以 JSON 存 SQLite，适合几十到几百个活跃房间的量级

## 已知问题与扩展方向

- 当前不做分布式房间同步，多实例部署需要引入 Redis Pub/Sub 或消息总线
- 当前不做断线重连恢复、观战、复杂权限模型
- 五子棋完整棋盘交互和高级规则未完成
- 可继续扩展：
  - Redis 房间广播
  - 更细的房间状态机
  - 更完整的伯明翰与五子棋玩法模块
