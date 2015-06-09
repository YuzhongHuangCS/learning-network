package org.pillowsky.securehttp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Calendar;

public class Peer implements Runnable {
	private String localAddr;
	private int localPort;
	private String remoteAddr;
	private int remotePort;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private DateFormat logFormat;

    public Peer(String localAddr, int localPort, String remoteAddr, int remotePort) throws IOException {
    	this.localAddr = localAddr;
        this.localPort = localPort;
        this.remoteAddr = remoteAddr;
        this.remotePort = remotePort;
        this.serverSocket = new ServerSocket(localPort, 50, InetAddress.getByName(localAddr));
        this.pool = Executors.newCachedThreadPool();
        this.logFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public void run() {
        while (true) {
            try {
                pool.execute(new RequestHandler(serverSocket.accept()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class RequestHandler implements Runnable {
        private Socket clientSocket;
        private String clientAddr;
        private int clientPort;
        
        RequestHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientAddr = socket.getInetAddress().getHostAddress();
            this.clientPort = socket.getPort();
        }

        @Override
        public void run() {
            try {
            	BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            	PrintWriter out = new PrintWriter(clientSocket.getOutputStream());

                String response;
                String line = in.readLine();
                String[] words = line.split(" ");
                while (in.readLine().length() > 0) {};

            	System.out.format("[%s] %s:%d %s%n", logFormat.format(Calendar.getInstance().getTime()), clientAddr, clientPort, line);

                switch (words[0]) {
                	case "GET":
                		switch (words[1]) {
                			case "/":
                				response = local();
                				break;
                			default:
                				response = notFound();
                		}
                		break;
                	case "POST":
                		switch (words[1]) {
	                		case "/remote":
	                			response = remote();
	                			break;
	                		case "/portal":
	                			response = portal();
	                			break;
	                		default:
	                			response = notFound();
                		}
                		break;
                	default:
                		response = notFound();
                }

                out.print(response);
                out.flush();
                clientSocket.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        private String local() {
        	StringBuilder body = new StringBuilder();
        	body.append("<form action='/remote' method='post'>");
        	body.append(String.format("<p>Local Page on %s:%d</p>", localAddr, localPort));
        	body.append(String.format("<p>Access From %s:%d</p>", clientAddr, clientPort));
        	body.append("<input type='submit' value='Visit Remote Page' />");
        	body.append("</form>");
        	return buildResponse(body, "200 OK");
        }
        
        private String remote() {
        	try {
        		Socket proxySocket = new Socket(remoteAddr, remotePort);

                PrintStream out = new PrintStream(proxySocket.getOutputStream());
                InputStreamReader in = new InputStreamReader(proxySocket.getInputStream());

                out.println("POST /portal HTTP/1.1");
                out.println();
                out.flush();

                StringBuilder body = new StringBuilder();
                char[] buffer = new char[8192];

                while (in.read(buffer) > 0) {
                	body.append(buffer);
                }

                proxySocket.close();
            	return body.toString();
        	} catch(IOException e) {
                e.printStackTrace();
                return buildResponse(e.toString(), "500 Internal Server Error");
            }
        }
        
        private String portal() {
        	StringBuilder body = new StringBuilder();
        	body.append(String.format("<p>Remote Page on %s:%d</p>", localAddr, localPort));
        	body.append(String.format("<p>Access From %s:%d</p>", clientAddr, clientPort));
        	body.append("<a href='/'><button>Visit Local Page</button></a>");
        	return buildResponse(body, "200 OK");
        }

        private String notFound() {
        	return buildResponse("404 Not Found", "404 Not Found");
        }
        
        private String buildResponse(CharSequence body, String textStatus) {
        	StringBuilder response = new StringBuilder();
        	response.append(String.format("HTTP/1.1 %s%n", textStatus));
        	response.append("Server: SecureHTTP\n");
        	response.append("Connection: close\n");
        	response.append("Content-Type: text/html; charset=UTF-8\n");
        	response.append(String.format("Content-Length: %d%n%n", body.length()));
        	response.append(body);
        	response.append("\n\n");
        	return response.toString();
        }
    }
}
