package fr.insalyon.tc;

import java.net.*;


public class FixedFileServer extends FileServer {

    public FixedFileServer(InetAddress clientAddress, int clientPort) {
        super(clientAddress, clientPort);
        this.cwnd = 3;
    }
}
