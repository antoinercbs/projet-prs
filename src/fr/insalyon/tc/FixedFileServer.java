package fr.insalyon.tc;

import java.net.*;


public class FixedFileServer extends FileServer {

    public FixedFileServer(InetAddress clientAddress, int clientPort) {
        super(clientAddress, clientPort);
        System.out.println("Initializing fixed cwnd size server");
        this.cwnd = 45;
        this.timeout = 60;
    }
}
