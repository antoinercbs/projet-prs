package fr.insalyon.tc;


import java.net.*;

import java.util.ArrayList;

public class CubicFileServer extends FileServer {

    //Variable pour refaire TCP
   boolean tcpFriendliness = true;
   boolean fastConvergence = true;
   double beta = 0.7;
   double c = 0.4;
   int wLastMax = 0;
   double epochStart = 0;
   double originPoint = 0;
   double minRtt = 0;
   int wTcp = 0;
   double k = 0;
   int ssthresh = Integer.MAX_VALUE;
   int cwndUpdate = 1;






    public CubicFileServer(InetAddress clientAddress, int clientPort) {
        super(clientAddress, clientPort);
    }

    void cubicReset() {
        this.wLastMax = 0;
        this.epochStart = 0;
        this.originPoint = 0;
        this.minRtt = 0;
        this.wTcp = 0;
        this.k = 0;
        this.ssthresh = 30;
    }

    void CAInitialization() {
        this.tcpFriendliness = true;
        this.fastConvergence = true;
        this.beta = 0.2;
        this.c = 0.4;
        this.cubicReset();
    }

    void CATimeout() {
        this.cubicReset();
    }


    void CAPacketLoss() {
        this.epochStart = 0;
        if (this.cwnd < this.wLastMax && this.fastConvergence) {
            this.wLastMax = (int) (this.cwnd*(2-this.beta)/2);
        } else {
            this.wLastMax = this.cwnd;
        }
        this.cwnd = (int) (this.cwnd*(1-this.beta));
        this.ssthresh = cwnd;
    }

    void CAOnData() {
        if (this.minRtt != 0) this.minRtt = Math.min(this.minRtt, this.rttManager.getSRtt()/1000.0);
        else this.minRtt = this.rttManager.getSRtt()/1000.0;
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
            this.wTcp = (int) (this.wTcp + (3*this.beta)/(2-this.beta) * t/(this.rttManager.getSRtt()/1000.0));
            if (this.wTcp>this.cwnd && this.wTcp > target) {
               /* this.maxCnt = this.cwnd/(this.wTcp-this.cwnd);
                if (this.cwndUpdate > this.maxCnt) this.cwndUpdate = this.maxCnt;*/
                this.cwndUpdate = (int) (this.cwnd + (this.wTcp-this.cwnd)/(double) this.cwnd);
            }
        }
    }

}
