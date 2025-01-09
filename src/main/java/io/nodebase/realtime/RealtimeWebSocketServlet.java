package io.nodebase.realtime;

import io.nodebase.auth.AuthService;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;

public final class RealtimeWebSocketServlet extends JettyWebSocketServlet {

    private final SubscriptionManager subscriptionManager;
    private final AuthService authService;

    public RealtimeWebSocketServlet(SubscriptionManager subscriptionManager, AuthService authService) {
        this.subscriptionManager = subscriptionManager;
        this.authService = authService;
    }

    @Override
    public void configure(JettyWebSocketServletFactory factory) {
        factory.setCreator((req, resp) -> new RealtimeWebSocketHandler(subscriptionManager, authService));
    }
}
