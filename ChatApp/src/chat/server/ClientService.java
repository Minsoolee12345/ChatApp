package chat.server;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class ClientService
{

    ChatServer chatServer;
    Socket socket;

    DataInputStream dis;
    DataOutputStream dos;

    String clientIP;
    String chatName;
    String displayName;

    final String quitCommand = "quit";
    final String renameCommand = "/rename";
    final String privateMessageCommand = "/to";
    final String imageCommand = "/img";
    final String getImageCommand = "/getimg";

    public ClientService(ChatServer chatServer, Socket socket) throws IOException
    {
        this.chatServer = chatServer;
        this.socket = socket;

        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        clientIP = socket.getInetAddress().getHostAddress();

        // 닉네임 설정 프로세스
        while (true) 
        {
            chatName = dis.readUTF();
            if (chatServer.isChatNameUnique(chatName)) 
            {
                displayName = chatName + "@" + clientIP;
                chatServer.addClientInfo(this);
                dos.writeUTF("NICK_OK"); // 클라이언트에게 닉네임 승인
                dos.flush();
                // "[입장]" 메시지를 다른 클라이언트에게 전송
                chatServer.sendToAll(this, "[입장] " + displayName);
                break;
            } 
            else
            {
                dos.writeUTF("NICK_IN_USE"); // 클라이언트에게 닉네임 중복 알림
                dos.flush();
            }
        }

        receive();
    }

    public void receive()
    {
        new Thread(() -> 
        {
            try {
                while (true) 
                {
                    String message = dis.readUTF();

                    if (message.startsWith(renameCommand)) 
                    {
                        handleRename(message);
                    } 
                    else if (message.startsWith(privateMessageCommand))
                    {
                        handlePrivateMessage(message);
                    } 
                    else if (message.startsWith(imageCommand)) 
                    {
                        handleImageReceive(message);
                    }
                    else if (message.startsWith(getImageCommand))
                    {
                        handleGetImage(message);
                    } 
                    else if (message.equals(quitCommand)) 
                    {
                        break;
                    } 
                    else 
                    {
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        String formattedMessage = "[" + displayName + "](" + timestamp + ") : " + message;
                        chatServer.sendToAll(this, formattedMessage);
                    }
                }
            } catch (IOException e) {
                System.out.println("클라이언트와의 연결이 끊어졌습니다: " + displayName);
            }
            quit();
        }).start();
    }

    private void handleRename(String message) 
    {
        String[] parts = message.split(" ", 2);
        if (parts.length == 2) 
        {
            String newName = parts[1].trim();
            if (newName.isEmpty()) 
            {
                send("닉네임은 공백일 수 없습니다.");
                return;
            }
            if (chatServer.isChatNameUnique(newName))
            {
                // 이전 이름 알림
                chatServer.sendToAll(this, "[닉네임 변경] " + displayName + " -> " + newName + "@" + clientIP);
                // 클라이언트 정보 업데이트
                chatServer.removeClientInfo(this);
                chatName = newName;
                displayName = chatName + "@" + clientIP;
                chatServer.addClientInfo(this);
                send("닉네임이 변경되었습니다: " + chatName);
            } 
            else
            {
                send("닉네임이 중복되었습니다.");
            }
        } 
        else
        {
            send("사용법: /rename 변경닉네임");
        }
    }

    private void handlePrivateMessage(String message) 
    {
        String[] parts = message.split(" ", 3);
        if (parts.length == 3) 
        {
            String recipientName = parts[1];
            String privateMessage = parts[2];
            chatServer.sendPrivateMessage(chatName, recipientName, privateMessage);
        }
        else
        {
            send("사용법: /to 닉네임 메시지");
        }
    }

    private void handleImageReceive(String message) 
    {
        try {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) 
            {
                String fileName = parts[1].trim();
                if (fileName.isEmpty()) 
                {
                    send("파일명이 공백일 수 없습니다.");
                    return;
                }

                // 이미지 크기 받기 (int 타입으로 수정)
                int fileSize = dis.readInt();
                if (fileSize <= 0)
                {
                    send("유효하지 않은 파일 크기입니다.");
                    return;
                }
                byte[] imageData = new byte[fileSize];
                dis.readFully(imageData);

                // 고유한 이미지 ID 생성
                String imageId = UUID.randomUUID().toString();
                chatServer.addImage(imageId, imageData);

                // 모든 클라이언트에게 이미지 전송 알림
                String notification = "[이미지 전송] " + displayName + "가 이미지를 전송했습니다: " + fileName
                        + " (ID: " + imageId + ")";
                chatServer.sendToAll(this, notification);
            }
        } catch (IOException e) {
            send("이미지 전송 실패: " + e.getMessage());
        }
    }

    private void handleGetImage(String message) 
    {
        String[] parts = message.split(" ", 2);
        if (parts.length == 2) 
        {
            String imageId = parts[1].trim();
            byte[] imageData = chatServer.getImage(imageId);
            if (imageData != null)
            {
                try {
                    dos.writeUTF("/imgdata " + imageId);
                    dos.flush();
                    dos.writeInt(imageData.length);
                    dos.write(imageData);
                    dos.flush();
                } catch (IOException e) {
                    send("이미지 전송 실패: " + e.getMessage());
                }
            } 
            else
            {
                send("이미지를 찾을 수 없습니다: " + imageId);
            }
        } 
        else
        {
            send("사용법: /getimg 이미지ID");
        }
    }

    public void send(String message) 
    {
        try {
            dos.writeUTF(message);
            dos.flush();
        } catch (IOException e) {
            System.out.println("메시지 전송 실패: " + e.getMessage());
        }
    }

    public void quit() 
    {
        chatServer.sendToAll(this, "[퇴장] " + displayName);
        chatServer.removeClientInfo(this);
        close();
    }

    public void close()
    {
        try {
            dis.close();
            dos.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("종료 중 오류 발생: " + e.getMessage());
        }
    }
}