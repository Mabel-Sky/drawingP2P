import java.net.Socket;

public class client {

    Socket serverSocket;
    String name;

    public client(Socket serverSocket, String name) {
        this.serverSocket = serverSocket;
        this.name = name;
    }
}