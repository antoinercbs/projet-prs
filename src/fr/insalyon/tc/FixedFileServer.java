package fr.insalyon.tc;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class FixedFileServer extends FileServer {




    public FixedFileServer(InetAddress clientAddress, int clientPort) {
        super(clientAddress, clientPort);
        this.cwnd = 40;
    }
}
