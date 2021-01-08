package fr.insalyon.tc;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class HubServer extends Thread {
    private DatagramSocket socket;
    private boolean running;
    private byte[] buf = new byte[256];

    public HubServer(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        running = true;
        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }

            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            String received = new String(packet.getData(), 0, packet.getLength()-1);
            System.out.println("Recu : " + received + " | longeur : " +  received.length());
            if (received.equals("SYN")) {
                FixedFileServer fs = new FixedFileServer(address, port);
                fs.start();
            }
        }
        socket.close();
    }
}
