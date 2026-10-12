package com.tpm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.tpm.realtime.StompAuthInterceptor;

/**
 * STOMP over WebSocket.
 * <ul>
 *   <li>{@code /topic/projects/{id}} - board events for everyone viewing a project</li>
 *   <li>{@code /topic/projects/{id}/presence} - who is currently viewing the board</li>
 *   <li>{@code /user/queue/notifications} - private per-user notifications</li>
 * </ul>
 * Uses the in-memory simple broker; to scale horizontally switch to {@code enableStompBrokerRelay}
 * (RabbitMQ/ActiveMQ) so every instance shares subscriptions.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties props;
    private final StompAuthInterceptor authInterceptor;
    private TaskScheduler brokerScheduler;

    public WebSocketConfig(AppProperties props, StompAuthInterceptor authInterceptor) {
        this.props = props;
        this.authInterceptor = authInterceptor;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setBrokerScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.brokerScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(props.corsAllowedOrigins().toArray(String[]::new));
        // Process each client's frames in order (CONNECT -> SUBSCRIBE -> ...), see presence snapshot.
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
