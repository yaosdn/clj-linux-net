package org.it4y.integration;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import junit.framework.Assert;
import org.it4y.net.link.LinkManager;
import org.it4y.net.link.NetworkInterface;
import org.it4y.net.tproxy.TProxyInterceptedSocket;
import org.it4y.net.tproxy.TProxyListener;
import org.it4y.util.Counter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

/**
 * You need to run src/test/scripts/setup-test.sh to be able to run this test
 * Created by luc on 1/10/14.
 */
public class TProxyListenerTest {
    final private Logger log=LoggerFactory.getLogger(TProxyListenerTest.class);

    @Test
    public void testTProxyListener() throws Exception {
        InetAddress bind= InetAddress.getByName("localhost");
        int port=1024;
        int backlog=10;

        TProxyListener listener=new TProxyListener(bind,port,backlog) {
            public void newClient(TProxyInterceptedSocket client) {}
            public void onIOError(IOException io) {}
        };

        Assert.assertNotNull(listener);
        listener.start();

        int cnt=0;
        while(!listener.isRunning() & cnt<10) {
            Thread.sleep(10);
            cnt++;
        }
        Assert.assertTrue(listener.isRunning());
        listener.halt();
        Thread.sleep(100);
        Assert.assertTrue(!listener.isRunning());
    }

    @Test
    public void testTProxyConnection() throws Exception {
        final Counter msgcnt=new Counter();
        InetAddress bind= InetAddress.getByName("localhost");
        int port=1800;
        int backlog=10;
        int retry=0;

        //lets use LinkManager to get information about our test tunnel
        LinkManager lm=new LinkManager();
        lm.start();
        while(!lm.isReady() & retry < 10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(lm.isReady());
        //get interface with default gateway. this will be our source IP
        final NetworkInterface defaultGW=lm.getDefaultGateway();
        Assert.assertNotNull(defaultGW);
        log.info("Default GW: {}",defaultGW);

        //our test device calls test
        final NetworkInterface tunnel=lm.findByInterfaceName("test");
        Assert.assertNotNull(tunnel);

        //Run a proxy to intercept port 80 . this requires setup-test.sh to setup iptables and routing
        TProxyListener listener=new TProxyListener(bind,port,backlog) {
            public void newClient(TProxyInterceptedSocket client) {
                log.info("intercept client connection: {}", client);
                //check client connection parameters
                Assert.assertNotNull(client);
                //we should check local and remote address are unchanged (thats transparent proxy)
                Assert.assertEquals(0x08080404,client.getRemoteAddress());
                Assert.assertEquals(80,client.getRemotePort());
                Assert.assertTrue(client.getSocket().toString().contains(defaultGW.getIpv4AddressAsInetAddress().toString()));
                //close connection on our side
                try {
                    client.getSocket().close();
                    log.info("client connection closed");
                } catch (IOException io) {}
                //this will signal message recieved
                msgcnt.inc();
            }
            public void onIOError(IOException io) {}
        };
        Assert.assertNotNull(listener);
        listener.start();
        retry=0;
        while(!listener.isRunning() & retry<10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(listener.isRunning());
        log.info("TProxy running...");
        //Now connect to our test IP (8.8.4.4) using HTTP
        Socket clientSocket = new Socket("8.8.4.4", 80);
        retry=0;
        while(!clientSocket.isConnected() & retry <10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(clientSocket.isConnected());
        log.info("Client connected...");
        //now wait until client connection is closed
        retry=0;
        while(msgcnt.getCount()!=1 & retry <10) {
            Thread.sleep(10);
            retry++;
        }
        clientSocket.close();
        log.info("Client disconnected");
        //we recieved 1 connection
        Assert.assertEquals(1,msgcnt.getCount());
        listener.halt();
        Thread.sleep(100);
        Assert.assertTrue(!listener.isRunning());
    }


    @Test
    public void testIOError() throws Exception {
        final Counter msgcnt=new Counter();
        final Counter errcnt=new Counter();
        InetAddress bind= InetAddress.getByName("localhost");
        int port=1800;
        int backlog=10;
        int retry=0;

        //lets use LinkManager to get information about our test tunnel
        LinkManager lm=new LinkManager();
        lm.start();
        while(!lm.isReady() & retry < 10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(lm.isReady());
        //get interface with default gateway. this will be our source IP
        final NetworkInterface defaultGW=lm.getDefaultGateway();
        Assert.assertNotNull(defaultGW);
        log.info("Default GW: {}",defaultGW);

        //our test device calls test
        final NetworkInterface tunnel=lm.findByInterfaceName("test");
        Assert.assertNotNull(tunnel);

        //Run a proxy to intercept port 80 . this requires setup-test.sh to setup iptables and routing
        TProxyListener listener=new TProxyListener(bind,port,backlog) {
            public void newClient(TProxyInterceptedSocket client) throws IOException {
                log.info("intercept client connection: {}", client);
                //check client connection parameters
                Assert.assertNotNull(client);
                //we should check local and remote address are unchanged (thats transparent proxy)
                Assert.assertEquals(0x08080404,client.getRemoteAddress());
                Assert.assertEquals(80,client.getRemotePort());
                Assert.assertTrue(client.getSocket().toString().contains(defaultGW.getIpv4AddressAsInetAddress().toString()));
                msgcnt.inc();
                throw new IOException("shit happens");
            }
            public void onIOError(IOException io) {
                errcnt.inc();
                log.info("we got some error: {}",io.getMessage());
                Assert.assertEquals("shit happens",io.getMessage());
            }
        };
        Assert.assertNotNull(listener);
        listener.start();
        retry=0;
        while(!listener.isRunning() & retry<10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(listener.isRunning());
        log.info("TProxy running...");
        //Now connect to our test IP (8.8.4.4) using HTTP
        Socket clientSocket = new Socket("8.8.4.4", 80);
        retry=0;
        while(!clientSocket.isConnected() & retry <10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(clientSocket.isConnected());
        log.info("Client connected...");
        //now wait until client connection is closed
        retry=0;
        while(msgcnt.getCount()!=1 & retry <10) {
            Thread.sleep(10);
            retry++;
        }
        clientSocket.close();
        log.info("Client disconnected");
        //we recieved 1 connection
        Assert.assertEquals(1,msgcnt.getCount());
        Assert.assertEquals(1,errcnt.getCount());
        listener.halt();
        Thread.sleep(100);
        Assert.assertTrue(!listener.isRunning());
    }


    @Test
    public void testPerformanceMultibleClients() throws Exception {
        final Counter msgcnt=new Counter();
        final Counter errcnt=new Counter();
        InetAddress bind= InetAddress.getByName("localhost");
        int port=1800;
        int backlog=10;
        int retry=0;

        //lets use LinkManager to get information about our test tunnel
        LinkManager lm=new LinkManager();
        lm.start();
        while(!lm.isReady() & retry < 10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(lm.isReady());
        //get interface with default gateway. this will be our source IP
        final NetworkInterface defaultGW=lm.getDefaultGateway();
        Assert.assertNotNull(defaultGW);
        log.info("Default GW: {}",defaultGW);

        //our test device calls test
        final NetworkInterface tunnel=lm.findByInterfaceName("test");
        Assert.assertNotNull(tunnel);

        //Run a proxy to intercept port 80 . this requires setup-test.sh to setup iptables and routing
        TProxyListener listener=new TProxyListener(bind,port,backlog) {
            public void newClient(TProxyInterceptedSocket client) throws IOException {
                //log.info("intercept client connection: {}", client);
                //check client connection parameters
                Assert.assertNotNull(client);
                //we should check local and remote address are unchanged (thats transparent proxy)
                Assert.assertEquals(0x08080404,client.getRemoteAddress());
                Assert.assertEquals(80,client.getRemotePort());
                Assert.assertTrue(client.getSocket().toString().contains(defaultGW.getIpv4AddressAsInetAddress().toString()));
                msgcnt.inc();
            }
            public void onIOError(IOException io) {
                errcnt.inc();
                log.info("oeps some error: {}", io.getMessage(),io);
            }
        };
        Assert.assertNotNull(listener);
        listener.start();
        retry=0;
        while(!listener.isRunning() & retry<10) {
            Thread.sleep(10);
            retry++;
        }
        Assert.assertTrue(listener.isRunning());
        log.info("TProxy running...");
        //we use NETTY to generated massive tcp connections
        EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors()*40);
        Bootstrap b = new Bootstrap(); // (1)
        b.group(workerGroup); // (2)
        b.channel(NioSocketChannel.class); // (3)
        b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
        Bootstrap handler = b.handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
            }
        });
        // Start the client.
        int nrOfConnections=4096;
        log.info("Starting {} connections ....",nrOfConnections);
        long start=System.currentTimeMillis();
        retry=0;
        for (int i=0;i<nrOfConnections;i++) {
            final ChannelFuture f = b.connect("8.8.4.4", 80);
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    //close when connected
                    if (future.isSuccess()) {
                        future.channel().disconnect();
                    }
                }
            });
        }
        //log.info("Started: {} msec", System.currentTimeMillis() - start);
        //wait until all are closed
        while (msgcnt.getCount()<nrOfConnections & retry<nrOfConnections) {
            Thread.sleep(10);
            retry++;
        }
        long end=System.currentTimeMillis();
        log.info("Done: {} msec", end - start);
        Assert.assertEquals(nrOfConnections, msgcnt.getCount());
        Assert.assertEquals(0, errcnt.getCount());
        listener.halt();
        Thread.sleep(100);
        Assert.assertTrue(!listener.isRunning());
    }


}
