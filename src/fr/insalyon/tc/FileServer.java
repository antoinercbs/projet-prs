package fr.insalyon.tc;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLOutput;

public class FileServer extends Thread {

    //Variables du réseau UDP/du fichier à servir
    private DatagramSocket socket;
    private InetAddress clientAdress;
    private int clientPort;
    private byte[] servedFileBytes;
    private byte[] buf = new byte[256]; //buffer de réception

    //Variables d'Etat de serveur (servant fichier ? Fini ? etc)
    private boolean running;
    private boolean isConnectionAck = false;

    //Variable pour refaire TCP
    private int lastAckSeg = -1;
    private int lastSendedSeg = 0;
    private int segSize = 1500;
    private int cwnd = 1;
    private int timeout = 20;

    //Variables de metrics
    private long startTime = 0;
    private long sendTime = 0;



    public FileServer(InetAddress clientAddress, int clientPort) {
        this.clientAdress = clientAddress;
        this.clientPort = clientPort;
        this.startTime = System.currentTimeMillis();
        try {
            initiateSocketOnRange(1000, 9999);
            this.socket.setSoTimeout(timeout);
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
                if (received.equals("ACK")) { //Si le client ACK la connexion avec ce serveur
                    System.out.println("Port selection acknoledged by client");
                    this.isConnectionAck = true;
                } else if (received.startsWith("ACK")) { //Si on a reçu l'ACK du dernier seg transmis
                    this.lastAckSeg = Math.max(this.lastAckSeg, getSegFromAck(received));
                    if (this.lastAckSeg == this.lastSendedSeg) { //si on a reçu tous les ACK correspondant à la cwnd
                        //System.out.println("RTT = " + (System.currentTimeMillis()-this.sendTime));
                        this.lastAckSeg = this.lastSendedSeg;
                        this.sendNextSegementGroup(++this.cwnd);
                    }
                } else  { //Sinon, c'est qu'on a demandé un fichier
                    System.out.println("File asked by client : " + received);
                    this.selectFile(received);
                    this.sendNextSegementGroup(this.cwnd);
                }
            } catch (SocketTimeoutException e) { //Si on reçoit pas les ACK à temps...
                if (this.isConnectionAck) {
                    this.cwnd--;
                    if (this.cwnd<=0) this.cwnd =1;
                    this.sendNextSegementGroup(this.cwnd);
                }
            } catch (IOException e) {
                running = false;
                e.printStackTrace();
            }
        }
        socket.close();
        System.out.println("Fermeture du socket, fin du thread");
    }


    private int getSegFromAck(String msg) {
        return Integer.parseInt(msg.substring(3))-1;
    }

    private void sendNextSegementGroup(int nbSegements) {
        try {
            for (this.lastSendedSeg= this.lastAckSeg+1; this.lastSendedSeg < this.lastAckSeg+1+nbSegements; this.lastSendedSeg++) {
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
        String binMsg = String.format("%06d", (segNumber +1)); //Le +1 est là car le client a été codé par un MATLABiste
        byte[] bin = new byte[this.segSize];
        int dataWindowSize = this.segSize -binMsg.length();
        if (dataWindowSize*segNumber >= this.servedFileBytes.length) {
            System.out.println("\nTéléchargement fini ! Debit moyen : " + this.calculateEndMeanRate() + " KB/S");
            binMsg = "FIN";
            bin = binMsg.getBytes();
            this.running = false;
        }
        else {
            for (int i = 0; i < binMsg.length(); i++) bin[i] = binMsg.getBytes()[i];
            int j = binMsg.length();
            for (int i = dataWindowSize * segNumber; i < Math.min((segNumber + 1) * dataWindowSize, this.servedFileBytes.length); i++) {
                bin[j++] = this.servedFileBytes[i];
            }
        }

        DatagramPacket synPacket = new DatagramPacket(bin, bin.length, clientAdress, clientPort);
        this.socket.send(synPacket);
        //this.sendTime = System.currentTimeMillis();
        //System.out.print("\rSended : " + binMsg + ", cwnd : " + this.cwnd);
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
