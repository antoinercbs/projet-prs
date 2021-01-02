package fr.insalyon.tc;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class CubicFileServer extends Thread {

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
   boolean tcpFriendliness = true;
   boolean fastConvergence = true;
   double beta = 0.2;
   double c = 0.4;
   double wLastMax = 0;
   double epochStart = 0;
   double originPoint = 0;
   double minRtt = 0;
   int wTcp = 0;
   double k = 0;
   double rtt = 0;
   int cwnd = 1;
   int ssthresh = Integer.MAX_VALUE;
   int cwndUpdate = 1;


    int timeout = 150;
    int lastAckSeg = -1;
    int lastSendedSeg = 0;
    int segSize = 1500;
    int redondantAckCount = 0;


    //Variables de metrics
    private long startTime = 0;
    private long sendTime = 0;



    public CubicFileServer(InetAddress clientAddress, int clientPort) {
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

    private void cubicReset() {
        this.wLastMax = 0;
        this.epochStart = 0;
        this.originPoint = 0;
        this.minRtt = 0;
        this.wTcp = 0;
        this.k = 0;
        this.ssthresh = Integer.MAX_VALUE;
    }

    private void cubicInitialization() {
        this.tcpFriendliness = true;
        this.fastConvergence = true;
        this.beta = 0.2;
        this.c = 0.4;
        this.cubicReset();
    }

    private void cubicTimeout() {
        this.cubicReset();
    }


    private void cubicPacketLoss() {
        this.epochStart = 0;
        if (this.cwnd < this.wLastMax && this.fastConvergence) {
            this.wLastMax = this.cwnd*(2*this.beta)/2.0;
        } else {
            this.wLastMax = this.cwnd;
        }
        this.cwnd = (int) (this.cwnd*(1-this.beta));
        this.ssthresh = cwnd;
    }

    private void cubicOnData() {
        if (this.minRtt != 0) this.minRtt = Math.min(this.minRtt, this.rtt);
        else this.minRtt = this.rtt;
        if (this.cwnd <= this.ssthresh) this.cwnd++;
        else {
            this.cubicUpdate();
        }
    }

    private void cubicUpdate() {
        //this.ackCnt++;
        if (this.epochStart <= 0) {
            this.epochStart = System.currentTimeMillis()/1000.0;
            if (this.cwnd < this.wLastMax) {
                this.k = Math.cbrt((this.wLastMax-this.cwnd)/this.c);
                this.originPoint = wLastMax;
            } else {
                this.k = 0;
                this.originPoint = cwnd;
            }
            //this.ackCnt = 1;
            this.wTcp = cwnd;
        }
        double t = System.currentTimeMillis()/1000.0 +this.minRtt -this.epochStart;
        double target = this.originPoint + this.c*Math.pow((t-this.k),3);
        if (target > this.cwnd) {
            //this.cwndUpdate = this.cwnd/(target-this.cwnd);
            this.cwndUpdate = (int) ((target-this.cwnd)/cwnd);
        } else {
            //this.cwndUpdate = 100*cwnd;
            this.cwndUpdate = (int) (this.cwnd + 0.01/this.cwnd);
        }
        if (this.tcpFriendliness) {
            //this.cubicTcpFriendliness();
            this.wTcp = (int) (this.wTcp + (3*this.beta)/(2-this.beta) * t/this.rtt);
            if (this.wTcp>this.cwnd && this.wTcp > target) {
               /* this.maxCnt = this.cwnd/(this.wTcp-this.cwnd);
                if (this.cwndUpdate > this.maxCnt) this.cwndUpdate = this.maxCnt;*/
                this.cwndUpdate = (int) (this.cwnd + (this.wTcp-this.cwnd)/this.cwnd);
            }
        }
    }



    public void run() {
        running = true;
        boolean firstRun = true;
        while (running) {
            if (firstRun) {
                firstRun = false;
                sendSynMsg();
                this.cubicInitialization();
                this.cubicOnData();
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
                    if (receivedAck == this.lastAckSeg) { //TODO : LE PROBLEME EST ICI !!!!
                        this.redondantAckCount++;
                        if (this.redondantAckCount >= 20) {
                            this.redondantAckCount = 0;
                            System.out.println("ACK redondant détecté !!!");
                            this.cubicPacketLoss();
                            this.sendNextSegementGroup(Math.max(this.cwnd, 1));
                        }
                    } else {
                        this.redondantAckCount = 0;
                        this.cubicOnData();
                        if (receivedAck == this.lastSendedSeg) { //si on a reçu tous les ACK correspondant à la cwnd
                            this.rtt = (System.currentTimeMillis()-this.sendTime)/1000.0;
                            System.out.println("rtt = " + this.rtt);
                            this.sendNextSegementGroup(Math.max(this.wTcp, 1));
                        }
                        this.lastAckSeg = Math.max(receivedAck, this.lastAckSeg);
                   }
                } else  { //Sinon, c'est qu'on a demandé un fichier
                    System.out.println("File asked by client : " + received);
                    this.selectFile(received);
                    this.sendNextSegementGroup(Math.max(this.cwnd, 1));
                }
            } catch (SocketTimeoutException e) { //Si on reçoit pas les ACK à temps...
                if (this.isConnectionAck) {
                    /*this.cwnd--;
                    if (this.cwnd<=0) this.cwnd =1;*/
                    System.out.println("Timout !");
                    this.sendNextSegementGroup(Math.max(this.cwnd, 1));
                    this.cubicTimeout();
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

    private void sendNextSegementGroup(int nbSegements) {
        try {
            for (this.lastSendedSeg= this.lastAckSeg+1; this.lastSendedSeg < this.lastAckSeg+1+nbSegements; this.lastSendedSeg++) {
                if (running) {
                    this.sendSegment(this.lastSendedSeg);
                }
                else break;
            }
            //this.lastSendedSeg--;
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
        this.sendTime = System.currentTimeMillis();
        System.out.print("\rSended : " + binMsg + ", cwnd : " + this.cwnd);
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
