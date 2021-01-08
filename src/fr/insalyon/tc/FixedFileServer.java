package fr.insalyon.tc;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class FixedFileServer extends Thread {

    //Variables du réseau UDP/du fichier à servir
    private DatagramSocket socket;
    private InetAddress clientAdress;
    private int clientPort;
    private byte[] servedFileBytes;
    private ArrayList<DatagramPacket> contentPackets;
    private byte[] buf = new byte[256]; //buffer de réception

    //Variables d'Etat de serveur (servant fichier ? Fini ? etc)
    private boolean running;
    private boolean isConnectionAck = false;

    //Variable pour refaire TCP
    int cwnd = 40;


    int timeout = 60;
    int maxAckSeg = -1;
    int lastAckSeg = -1;
    int lastSendedSeg = 0;
    int segSize = 1500;
    int redondantAckCount = 0;


    //Variables de metrics
    private long startTime = 0;
    private long sendTime = 0;



    public FixedFileServer(InetAddress clientAddress, int clientPort) {
        this.clientAdress = clientAddress;
        this.clientPort = clientPort;
        this.startTime = System.currentTimeMillis();
        try {
            initiateSocketOnRange(1000, 9999);
            this.socket.setSoTimeout(this.timeout);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }




    public void run() {
        running = true;
        boolean firstRun = true;
        while (running) {
            if (firstRun) {
                firstRun = false;
                sendSynMsg();

            }
            try {
                String received = receiveString();
                if (!isConnectionAck) {
                    if (received.equals("ACK")) { //Si le client ACK la connexion avec ce serveur
                        System.out.println("Port selection acknoledged by client");
                        this.isConnectionAck = true;
                    }
                } else if (received.startsWith("A")) { //Si on a reçu l'ACK du dernier seg transmit
                    int receivedAck = getSegFromAck(received);
                    if (receivedAck == this.lastAckSeg) {
                        this.redondantAckCount++;
                        if (this.redondantAckCount >= 3) {
                            //this.redondantAckCount = 0;
                            //System.out.println("Redondant ACK");
                            this.sendSegment(this.lastAckSeg+1);
                        }
                    } else {
                        this.redondantAckCount = 0;
                        while (receivedAck >= this.lastSendedSeg - this.cwnd) {
                            sendSegment(++this.lastSendedSeg);
                        }
                    }
                    this.lastAckSeg = receivedAck;

                } else  { //Sinon, c'est qu'on a demandé un fichier
                    System.out.println("File asked by client : " + received);
                    this.selectFile(received);
                    this.contentPackets = this.initiateContentPackets();
                    this.sendSegment(0);
                }
            } catch (SocketTimeoutException e) { //Si on reçoit pas les ACK à temps...
                if (this.isConnectionAck) {
                    /*this.cwnd--;
                    if (this.cwnd<=0) this.cwnd =1;*/

                    System.out.println("Timout !");
                    //this.cubicPacketLoss();
                    try {
                        this.sendSegment(this.lastAckSeg+1);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    //this.sendSegmentGroup(this.maxAckSeg+1, Math.max(this.cwnd, 1));

                }
            } catch (IOException e) {
                running = false;
                e.printStackTrace();
            }
        }
        socket.close();
        System.out.println("Fermeture du socket, fin du thread");
    }




    private int getSegFromAck(String msg) { //Les segments commencent à 1
        return Integer.parseInt(msg.substring(3))-1;
    }

    private void sendSegmentGroup(int firstSegment,int nbSegements) {
        try {
            for (this.lastSendedSeg= firstSegment; this.lastSendedSeg < firstSegment+nbSegements; this.lastSendedSeg++) {
                if (running) {
                    this.sendSegment(this.lastSendedSeg);
                }
                else break;
            }
            this.lastSendedSeg--;
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private double calculateEndMeanRate() {
        return this.servedFileBytes.length/(System.currentTimeMillis()-this.startTime);
    }

    private String receiveString() throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        String received = new String(packet.getData(), 0, packet.getLength()-1);
        return received;
    }

    private void sendSynMsg() {
        String synMsg = "SYN-ACK"+ socket.getLocalPort();
        DatagramPacket synPacket = new DatagramPacket(synMsg.getBytes(), synMsg.length(), clientAdress, clientPort);
        try {
            this.socket.send(synPacket);
        } catch (IOException e) {
            e.printStackTrace();
            this.running = false;
        }
        System.out.println("Sended : " + synMsg);
    }

    private void sendSegment(int segNumber) throws IOException { //TODO Utiliser un stream peut être plus efficace qu'une variable !
        if (segNumber >= this.contentPackets.size()) {
            System.out.println("\nTéléchargement fini ! Debit moyen : " + this.calculateEndMeanRate() + " KB/S");
            this.running = false;
        } else if (segNumber < this.contentPackets.size()) {
            this.socket.send(this.contentPackets.get(segNumber));
            this.sendTime = System.currentTimeMillis();
        }

    }

    private DatagramPacket createPacketForSegment(int segNumber) {
        String binMsg = String.format("%06d", (segNumber +1)); //Le +1 est là car le client a été codé par un MATLABiste
        byte[] bin = new byte[this.segSize];
        int dataWindowSize = this.segSize -binMsg.length();
        if (dataWindowSize*segNumber > this.servedFileBytes.length) {
            binMsg = "FIN";
            bin = binMsg.getBytes();
        }
        else {
            for (int i = 0; i < binMsg.length(); i++) bin[i] = binMsg.getBytes()[i];
            int j = binMsg.length();
            for (int i = dataWindowSize * segNumber; i < Math.min((segNumber + 1) * dataWindowSize, this.servedFileBytes.length); i++) {
                bin[j++] = this.servedFileBytes[i];
            }
        }

        return new DatagramPacket(bin, bin.length, clientAdress, clientPort);

    }

    private ArrayList<DatagramPacket> initiateContentPackets() {
        ArrayList<DatagramPacket> array = new ArrayList<>();
        int index = 0;
        while (true) {
            array.add(createPacketForSegment(index++));
            if (new String(array.get(array.size()-1).getData(), StandardCharsets.UTF_8).equals("FIN")) {
                return array;
            }
        }

    }

    private void sendEndSegment() throws IOException {

    }

    private void selectFile(String filePath) {
        try {
            this.servedFileBytes = Files.readAllBytes(Path.of(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initiateSocketOnRange(int minPort, int maxPort)  throws SocketException{
        for (int i = minPort; i <= maxPort; i++) {
            try {
                this.socket = new DatagramSocket(i);
                System.out.println("Port choisi : " + i);
                return;
            } catch (IOException ex) {
                continue;
            }
        }
        throw new SocketException("no free port found on [" + minPort +":" + maxPort + "]");
    }
}
