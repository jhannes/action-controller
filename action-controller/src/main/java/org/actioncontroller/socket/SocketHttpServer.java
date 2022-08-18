package org.actioncontroller.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketHttpServer {
    private final InetSocketAddress socketAddress;
    private ServerSocket serverSocket;
    private ApiSocketAdapter adapter;

    public SocketHttpServer(InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    public void createContext(String prefix, ApiSocketAdapter adapter) {
        this.adapter = adapter;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(socketAddress.getPort());
        Thread thread = new Thread(this::handleClients);
        thread.setName(getClass().getSimpleName() + "-thread");
        thread.start();
    }

    private void handleClients() {
        while (!Thread.interrupted()) {
            try {
                try (Socket socket = serverSocket.accept()) {
                    adapter.handle(socket);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int getAddress() {
        return serverSocket.getLocalPort();
    }
}
