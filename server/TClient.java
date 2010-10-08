import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.trend.Packet;


public class TClient {
    public static final String host = "localhost";
    public static final int CLIENT_PORT = 1006;
    private Selector selector = null;
    private SocketChannel srvChannel = null;
    private SocketChannel cltChannel = null;
    private boolean isServerReady = false;
    
    private ByteBuffer cltBuf = ByteBuffer.allocateDirect(1024);
    private ByteBuffer srvBuf = ByteBuffer.allocateDirect(1024);
    
    public TClient() throws Exception{
    	// Create a selector
    	selector = Selector.open();
    	// Connect to server
    	connectServer();
    	
    	cltBuf.clear();
    	srvBuf.clear();
    }
    
    private void writeDataToChannel (SocketChannel channel, ByteBuffer buf) throws Exception{
    	while (buf.hasRemaining()) {
    		channel.write(buf);			
		}
    }
        
    private void readDataFromClient (SelectionKey key) throws Exception {
    	SocketChannel sc = (SocketChannel) key.channel();
    	int count = 0;    	
    	
    	cltBuf.clear();
    	
    	while ((count = sc.read(cltBuf)) > 0) {
    		cltBuf.flip();
    		//sc.write(cltBuf);
    		writeDataToChannel(srvChannel, cltBuf);
    		cltBuf.clear();
    	}
    	
    	if (count < 0) {
    		sc.close();
    	}
    }
    
    private void readDataFromServer (SelectionKey key) throws Exception {
    	SocketChannel sc = (SocketChannel) key.channel();
    	int count = 0;
    	
    	srvBuf.clear();
    	
    	while ((count = sc.read(srvBuf)) > 0) {
    		srvBuf.flip();
    		writeDataToChannel(cltChannel, srvBuf);    		
    		srvBuf.clear();
    	}
    	
    	if (count < 0) {
    		sc.close();
    	}
    }
    
    public void waitFromConsole() throws Exception{
    	if (!isServerReady) {
    		System.out.println("Server is not ready, terminate now");
    		return;
    	}
    	    	
    	ServerSocketChannel ssc = ServerSocketChannel.open();
    	ssc.configureBlocking(false);
    	ServerSocket ss = ssc.socket();
    	InetSocketAddress address = new InetSocketAddress(CLIENT_PORT);
    	ss.bind(address);
    	ssc.register(selector, SelectionKey.OP_ACCEPT);
    	System.out.println("wait to console on " + CLIENT_PORT + "...");
    	
    	try {
    		while (true) {			
    			int num = selector.select();
    			
    			System.out.println("Get selection " + num);
    			
    			// nothing to do
    			if (num == 0) continue;
    				

    			Set<SelectionKey> selectedKeys = selector.selectedKeys();
    			Iterator<SelectionKey> it = selectedKeys.iterator();

    			while (it.hasNext()) {
    				SelectionKey key = (SelectionKey) it.next();

    				// Is it a new connection?
    				if ( key.isAcceptable() ) {					
    					ServerSocketChannel svrSocketChannel = (ServerSocketChannel) key.channel();
    					cltChannel = svrSocketChannel.accept();
    					cltChannel.configureBlocking(false);
    					cltChannel.register(selector, SelectionKey.OP_READ);
    					System.out.println("Accept a console connection from " + cltChannel);					
    				}
    				// Read the data
    				else if (key.isReadable()) {
    					System.out.println ("A readable key is coming");
    					boolean isFromServer = (key.channel() == srvChannel);
    					if (isFromServer) {
    						readDataFromServer(key);
    					} else {
    						readDataFromClient(key);
    					}
    				}
    				
    				it.remove();
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();    		
    	}
    	finally {
    		try {
    			if (srvChannel != null) srvChannel.close();
    			if (cltChannel != null) cltChannel.close();
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
	}
    
    private void connectServer(){        
        try {
        	InetSocketAddress address = new InetSocketAddress(host, TServer.SERVER_PORT);
        	srvChannel = SocketChannel.open();
        	srvChannel.configureBlocking(false);
        	srvChannel.connect(address);
        	
        	// Wait the connection ready
        	while (!srvChannel.finishConnect()) {
        		Thread.sleep(50);
        	}        	
        	srvChannel.register(selector, SelectionKey.OP_READ);        	
            isServerReady = true;
            
            System.out.println("Connect to server on " + address);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
//        finally {
//            if (srvChannel != null) {
//            	try {
//            		srvChannel.close();
//            		srvChannel = null;
//            	}
//            	catch (Exception e) {
//            		e.printStackTrace();
//            	}
//            }
//        }
    }
    
    
    public static void main(String[] args) {
        try {
            new TClient().waitFromConsole();
        }
        catch (Exception e) {
            System.out.println("Fatal:"+e.getMessage());
        }
        System.out.println("Client is terminated");
    }
}
