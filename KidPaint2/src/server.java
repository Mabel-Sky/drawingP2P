import javax.xml.crypto.Data;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;

public class server extends Thread {

    class action { }

    class client {
        String name;
        Socket socket;
        public client(String name, Socket socket) {
            this.name = name;
            this.socket = socket;
        }
    }

    class onBoarding extends Thread{
        private ServerSocket serverSocket;
        private ArrayList<client> clientList;
        String groupName;

        public onBoarding(ServerSocket serverSocket, ArrayList<client> clientList, String groupName) {
            this.serverSocket = serverSocket;
            this.clientList = clientList;
            this.groupName = groupName;
        }

        @Override
        public void run() {
            while(true) {
                try {
                    Socket temp = this.serverSocket.accept();

                    // identify incoming packet
                    DataInputStream in = new DataInputStream(temp.getInputStream());
                    DataOutputStream out = new DataOutputStream(temp.getOutputStream());

                    switch (in.readInt()) {
                        case 101:
                            // onboarding
                            String name = "";
                            byte[] data = in.readNBytes(in.readInt());
                            for (int i = 0; i != data.length; i++) {
                                name += (char) data[i];
                            }
                            client newClient = new client(name,temp);
                            synchronized (this.clientList) {
                                this.clientList.add(newClient);
                            }
                            System.out.println("[server] onboarded player: " + name);
                            out.writeInt(100);
                            out.flush();
                            break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    class advertiser extends Thread {
        DatagramSocket server;
        int port = 38878;
        private byte[] message;

        private byte[] buf = new byte[256];

        public advertiser(int port,InetAddress addr, String name) throws SocketException {
            server = new DatagramSocket(this.port);
            message = (name + " " + addr.toString() + " " + port).getBytes();
        }

        @Override
        public void run() {
            while(true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf,buf.length);
                    server.receive(packet);
                    byte[] data = packet.getData();
                    String temp = "";
                    int index = 0;
                    while (true) {
                        if (data[index] == 0) { break; }
                        temp += (char) data[index++];
                    }
                    if (temp.equals("*wave*")) {
                        System.out.println("[server] broadcast received");
                        InetAddress sender = packet.getAddress();
                        int port = 38879;
                        System.out.println("[server] sending advert to " + sender.toString() +":" + port);
                        DatagramPacket advert = new DatagramPacket(message,message.length,sender,port);
                        server.send(advert);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    int port = 38877;
    ArrayList<client> clientList = new ArrayList<>();
    String name;
    ServerSocket serverSocket;
    ArrayList<client> disconnected = new ArrayList<>();
    public server(String groupName, serverSharedData data) throws IOException {
        // setup
        serverSocket = new ServerSocket(port);
        System.out.println("inet: " + serverSocket.getInetAddress().getHostAddress());
        data.setData(serverSocket.getInetAddress(),port);
        name = groupName;

        Thread onboard = new Thread(new onBoarding(serverSocket,clientList,name));
        Thread advertise = new Thread(new advertiser(port,serverSocket.getInetAddress(),name));
        onboard.start();
        advertise.start();

        System.out.println("Server up...");
        System.out.println("bound ports: \n-38877\n-38878");
    }

    @Override
    public void run() {
        while (true) {
            // registered users loop
            synchronized (clientList) {
                for (int i = 0; i != clientList.size(); i++) {
                    client temp = clientList.get(i);
                    try {
                        DataInputStream in = new DataInputStream(temp.socket.getInputStream());
                        DataOutputStream out = new DataOutputStream(temp.socket.getOutputStream());
                        if (in.available() != 0) {
                            // Actions
                            switch (in.readInt()) {
                                case 202:
                                    // echo
                                    System.out.println("[server] Action code 202");
                                    String message = "echo";
                                    out.writeInt(message.length());
                                    out.writeBytes(message);
                                    out.flush();
                                    break;
                                case 300:
                                    // message
                                    int size = in.readInt();
                                    byte[] chat = in.readNBytes(size);
                                    System.out.println("[server] Action code 300 - Message");
                                    synchronized(clientList){
                                        clientList.removeAll(disconnected);
                                        for(client c : clientList){
                                            try{
                                                DataOutputStream clientout = new DataOutputStream(c.socket.getOutputStream());
                                                clientout.writeInt(300);
                                                clientout.writeInt(size);
                                                clientout.write(chat);
                                                clientout.flush();
                                            }
                                            catch (IOException e){
                                                System.out.println("[server] Client disconnected: " + c.name);
                                                disconnected.add(temp);
                                            }
                                        }
                                    }
                                    disconnected.clear();
                                    break;
                                case 400:
                                    int len= in.readInt();
                                    byte[] drawingUpdate = in.readNBytes(len);
                                    System.out.println("[server] Action code 400 - Drawing Action");
                                    synchronized (clientList){
                                        clientList.removeAll(disconnected);
                                        for(client c: clientList){
                                            try{
                                                DataOutputStream clientout = new DataOutputStream(c.socket.getOutputStream());
                                                clientout.writeInt(400);
                                                clientout.writeInt(len);
                                                clientout.write(drawingUpdate);
                                                clientout.flush();
                                            }
                                            catch(IOException ex){
                                                System.out.println("[server] Client disconnected: " + c.name);
                                                disconnected.add(temp);
                                            }
                                        }
                                    }
                                    disconnected.clear();
                                    break;
                                default:
                                    System.out.println("[server] This is NOT supposed to happen");
                                    break;
                            }

                        }
                    } catch (IOException e) {
                        System.out.println("[server] Client disconnected: " + temp.name);
                        disconnected.add(temp);
                    }
                }
            }
            clientList.removeAll(disconnected);
            disconnected.clear();

        }
    }
}
