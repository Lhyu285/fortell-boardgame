package com.fortell.boardgame.config;

import com.fortell.boardgame.websocket.BoardGameWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final BoardGameWebSocketHandler webSocketHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(BoardGameWebSocketHandler webSocketHandler,
                           @Value("${app.allowed-origin-patterns}") String allowedOriginPatterns) {
        this.webSocketHandler = webSocketHandler;
        this.allowedOriginPatterns = allowedOriginPatterns.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/rooms/{gameType}/{roomId}")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
