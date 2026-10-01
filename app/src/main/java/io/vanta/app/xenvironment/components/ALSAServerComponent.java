package io.vanta.app.xenvironment.components;

import io.vanta.app.alsaserver.ALSAClient;
import io.vanta.app.alsaserver.ALSAClientConnectionHandler;
import io.vanta.app.alsaserver.ALSARequestHandler;
import io.vanta.app.xconnector.UnixSocketConfig;
import io.vanta.app.xconnector.XConnectorEpoll;
import io.vanta.app.xenvironment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final UnixSocketConfig socketConfig;
    private final ALSAClient.Options options;

    public ALSAServerComponent(UnixSocketConfig socketConfig, ALSAClient.Options options) {
        this.socketConfig = socketConfig;
        this.options = options;
    }

    @Override
    public void start() {
        if (connector != null) return;
        ALSAClient.assignFramesPerBuffer(environment.getContext());
        connector = new XConnectorEpoll(socketConfig, new ALSAClientConnectionHandler(options), new ALSARequestHandler());
        connector.setMultithreadedClients(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.destroy();
            connector = null;
        }
    }
}
