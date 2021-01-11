package fr.insalyon.tc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class PacketGenerator {

    private final int segSize;
    private final int dataSize;
    private final int headerSize;
    private final long sizeInPackets;
    private final long fileSize;
    private InetAddress clientAdress;
    private int clientPort;
    private RandomAccessFile file;

    public PacketGenerator(int segSize, String path, InetAddress clientAdress, int clientPort) {
        this.segSize = segSize;
        this.headerSize = 6;
        this.dataSize = this.segSize - this.headerSize;
        this.clientAdress = clientAdress;
        this.clientPort = clientPort;
        try {
            this.file = new RandomAccessFile(path, "r");
        } catch (FileNotFoundException e) {
            System.out.println("Erreur d'accès au fichier");
            e.printStackTrace();
        }
        long length = 0;
        try {
            length = this.file.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.fileSize = length;
        this.sizeInPackets = length/this.dataSize + 1;
        System.out.println(this.sizeInPackets + " packets de " + this.dataSize + " octets utiles à envoyer.");

    }



    public DatagramPacket readPacketForSegment(int segNumber) {
        String binMsg = String.format("%06d", (segNumber +1)); //Le +1 est là car le client a été codé par un MATLABiste
        byte[] bin = new byte[(segNumber == this.sizeInPackets-1) ? (int) (this.fileSize%this.dataSize+6): this.segSize];
        try {
            for (int i = 0; i < this.headerSize; i++) bin[i] = binMsg.getBytes()[i];
            file.seek(dataSize*segNumber);
            int bytesRead = file.read(bin, this.headerSize, bin.length-this.headerSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new DatagramPacket(bin, bin.length, clientAdress, clientPort);
    }

    public DatagramPacket getFinPacket() {
        String binMsg = "FIN";
        byte[] bin = binMsg.getBytes();
        return new DatagramPacket(bin, bin.length, clientAdress, clientPort);

    }

    public long getFileSize() {
        return this.fileSize;
    }

    public long getSizeInPackets() {
        return this.sizeInPackets;
    }
}
