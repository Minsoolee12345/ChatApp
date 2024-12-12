package chat.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer 
{

    final String quitCommand = "quit";
    ServerSocket serverSocket;
    Map<String, ClientService> chatClientInfo = new ConcurrentHashMap<>();

    // 이미지 저장소: imageId -> 이미지 바이트 배열
    Map<String, byte[]> imageStore = new ConcurrentHashMap<>();

    public void start(int portNo)
    {
        try {
            serverSocket = new ServerSocket(portNo);
            System.out.println("[채팅서버] 시작 (" + InetAddress.getLocalHost() + ":" + portNo + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectClient()
    {
        Thread thread = new Thread(() -> 
        {
            try {
                while (true) 
                {
                    Socket socket = serverSocket.accept();
                    new ClientService(this, socket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() 
    {
        try {
            serverSocket.close();
            System.out.println("[채팅서버] 종료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isChatNameUnique(String chatName) 
    {
        return !chatClientInfo.containsKey(chatName);
    }

    public synchronized void addClientInfo(ClientService clientService)
    {
        chatClientInfo.put(clientService.chatName, clientService);
        System.out.println("[입장] " + clientService.displayName + "(채팅 참여자 수 : " + chatClientInfo.size() + ")");
    }

    public synchronized void sendToAll(ClientService sender, String message) 
    {
        for (ClientService cs : chatClientInfo.values()) 
        {
            if (cs != sender)// 메시지를 보낸 클라이언트를 제외하고 전송
            { 
                cs.send(message);
            }
        }
    }

    public synchronized void sendPrivateMessage(String senderName, String recipientName, String message) 
    {
        ClientService recipient = chatClientInfo.get(recipientName);
        if (recipient != null) 
        {
            String formattedMessage = "[" + senderName + " -> " + recipientName + "] : " + message;
            recipient.send(formattedMessage);
        } 
        else
        {
            // 발신자에게 수신자가 없음을 알림
            ClientService sender = chatClientInfo.get(senderName);
            if (sender != null) 
            {
                sender.send("수신자를 찾을 수 없습니다: " + recipientName);
            }
        }
    }

    public synchronized void removeClientInfo(ClientService clientService) 
    {
        chatClientInfo.remove(clientService.chatName);
        System.out.println("[퇴장] " + clientService.displayName + "(채팅 참여자 수 : " + chatClientInfo.size() + ")");
    }

    // 이미지 저장 메서드
    public void addImage(String imageId, byte[] imageData) 
    {
        imageStore.put(imageId, imageData);
    }

    // 이미지 요청 시 이미지 데이터 반환 메서드
    public byte[] getImage(String imageId)
    {
        return imageStore.get(imageId);
    }

    public static void main(String[] args)
    {

        final int portNo = 50005;

        ChatServer chatServer = new ChatServer();

        chatServer.start(portNo);
        chatServer.connectClient();

        Scanner sc = new Scanner(System.in);
        while (true) 
        {
            System.out.println("서버를 종료하려면 quit을 입력하고 Enter를 치세요");
            String command = sc.nextLine();
            if (command.equalsIgnoreCase(chatServer.quitCommand))
                break;
        }
        chatServer.stop();
        sc.close();
    }
}
