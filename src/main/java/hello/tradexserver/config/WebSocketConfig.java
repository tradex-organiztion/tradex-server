package hello.tradexserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    //WebSocket 메시지 브로커를 구성
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config){
        //특정 목적지 경로(prefix)로 들어오는 메시지를 구독 중인 클라이언트에게 브로드캐스트합니다.
        config.enableSimpleBroker("/topic");
        //특정 목적지로 메시지를 보낼 수 있도록 합니다.
        config.setApplicationDestinationPrefixes("/app");
    }

    //클라이언트의 엔드포인트를 등록하고 SockJS를 사용하도록 설정합니다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        //엔드포인트를 등록하고 SockJS를 사용하도록 설정합니다.
        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
    }
}

