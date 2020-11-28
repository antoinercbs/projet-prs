package fr.insalyon.tc;

public class Main {

    public static void main(String[] args) {
        int portNum = 0;

        if (args.length != 1) {
            System.err.println("Syntaxe : ./serveur1-antoine <port>");
            System.exit(1);
        }
        try {
            portNum = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Argument" + args[0] + " must be an integer.");
            System.exit(1);
        }
        System.out.println("Port choisi : " + portNum + ",lancement...");
        HubServer serv = new HubServer(portNum);
        serv.start();

    }
}
