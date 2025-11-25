import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.crypto.Data;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
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

    private ArrayList<BufferedImage> animationFrames = new ArrayList<>();
    private boolean isRecording = false ;
    private String recorderUsername = "" ;


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
                    try {
                        client temp = clientList.get(i);
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
                                case 401 :
                                    // start recording
                                    System.out.println("[server] Action code 401 : Start Recording ");
                                    int nameLen = in.readInt();
                                    String username = new String (in.readNBytes(nameLen));
                                    recorderUsername = username ;
                                    isRecording = true ;
                                    animationFrames.clear(); // Reset frames for new recording
                                    break ;
                                case 402 :
                                    // stop recording
                                    System.out.println("[server] Action code 402 : Stop Recording ");
                                    isRecording = false ;
                                    break ;
                                case 404 :
                                    System.out.println("[server] Action code 404 : Play recording  ");
                                    out.writeInt(animationFrames.size()); // Send total frames
                                    for (BufferedImage frame : animationFrames) {
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ImageIO.write(frame, "png", baos);
                                        byte[] frameBytes = baos.toByteArray();
                                        out.writeInt(frameBytes.length);
                                        out.write(frameBytes);
                                    }
                                    out.flush();
                                    break;
                                case 405 :
                                    // receive frame
                                    System.out.println("[server] Action code 405 : Receive Frame ");
                                    int size = in.readInt();
                                    byte[] imgData = in.readNBytes(size);
                                    BufferedImage frame = ImageIO.read(new java.io.ByteArrayInputStream(imgData));
                                    if (isRecording) {
                                        animationFrames.add(frame);
                                        System.out.println("[server] Frame added. Total frames: " + animationFrames.size());
                                    } else {
                                        System.out.println("[server] Not recording. Frame discarded.");
                                    }
                                    break ;
                                case 406 :
                                    // export recording to gif
                                    System.out.println("[server] Action code 406 : Export Recording to GIF ");
                                    if (!isRecording && temp.name.equals(recorderUsername)){
                                        int pathLen = in.readInt();
                                        String filePath = new String (in.readNBytes(pathLen));
                                        exportFramesToGIF(animationFrames, filePath);
                                        System.out.println("[server] Exported recording to " + filePath);
                                    }
                                    break ;

                            }

                        }
                    } catch (IOException e) {}
                }
            }

        }
    }

    private void exportFramesToGIF(ArrayList<BufferedImage> animationFrames, String filePath) {
        try{
            File outputFile = new File(filePath);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile);

            writer.prepareWriteSequence(null);
            for (BufferedImage frame : animationFrames) {
                writer.writeToSequence(new IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();

            ios.close();
            writer.dispose();// Clean up resources
            System.out.println("[server] GIF exported successfully to: " + filePath);
        }catch (Exception e){
            System.out.println("[server] Error exporting GIF: " + e.getMessage());
        }
    }
}
