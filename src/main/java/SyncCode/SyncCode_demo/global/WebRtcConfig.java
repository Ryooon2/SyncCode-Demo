package SyncCode.SyncCode_demo.global;

import SyncCode.SyncCode_demo.domain.rtc.KurentoHandler;
import SyncCode.SyncCode_demo.domain.rtc.KurentoRoomManager;
import SyncCode.SyncCode_demo.domain.rtc.KurentoUserRegistry;
import lombok.RequiredArgsConstructor;
import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebRtcConfig implements WebSocketConfigurer {
    // Kurento Media Server 위치
    @Value("${kurento.client.websocket.url}")
    private String websocketUrl;

    // Kurento를 다루기 위한 핸들러
    private final KurentoUserRegistry registry;

    @Bean
    public KurentoRoomManager kurentoRoomManager(){
        // 실험. 안되면 지우기
        return new KurentoRoomManager(kurentoClient());
    }

    @Bean
    public KurentoHandler kurentoHandler() {
        return new KurentoHandler(registry, kurentoRoomManager());
    }


    // 쉽게 말하자면 Kurento Media Server와 연결하는 client
    @Bean
    public KurentoClient kurentoClient(){
        return KurentoClient.create(websocketUrl);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer(){
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(32768);
        container.setMaxBinaryMessageBufferSize(32768);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kurentoHandler(), "/signal").setAllowedOrigins("*");
    }
}
