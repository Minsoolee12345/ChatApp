package chat.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat; // 추가
import java.util.Date; // 추가
import java.util.Scanner;

public class ChatClient 
{
    String chatName;
    Socket socket;

    DataInputStream dis;
    DataOutputStream dos;

    final String quitCommand = "quit";
    final String renameCommand = "/rename";
    final String privateMessageCommand = "/to";
    final String imageCommand = "/img";
    final String getImageCommand = "/getimg";

    // 상태 플래그 추가
    private volatile boolean isQuitting = false;

    // 수정된 connect 메서드
    public void connect(String serverIP, int portNo, String initialName, Scanner sc) 
    {
        try {
            socket = new Socket(serverIP, portNo);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            String desiredName = initialName;
            sendRaw(desiredName); // 초기 대화명 전송
            System.out.println("닉네임을 서버로 전송 중: " + desiredName);

            while (true) {
                String response = dis.readUTF();
                if (response.equals("NICK_OK")) {
                    this.chatName = desiredName; // 서버에서 닉네임이 승인되었을 때만 설정
                    System.out.println("닉네임이 설정되었습니다: " + chatName);
                    break;
                } else if (response.equals("NICK_IN_USE")) {
                    System.out.println("닉네임이 중복되었습니다. 다른 닉네임을 입력하세요:");
                    desiredName = sc.nextLine().trim();
                    if (desiredName.isEmpty()) {
                        System.out.println("대화명은 공백일 수 없습니다.");
                        continue;
                    }
                    sendRaw(desiredName); // 새로운 닉네임 전송
                    System.out.println("새 닉네임을 서버로 전송 중: " + desiredName);
                } else {
                    System.out.println("알 수 없는 응답을 받았습니다: " + response);
                }
            }

            System.out.println("[" + chatName + "] 채팅 서버 연결 성공 (" + serverIP + ":" + portNo + ")");

        } catch (IOException e) {
            System.out.println("서버에 연결할 수 없습니다: " + e.getMessage());
            System.exit(1); // 연결 실패 시 프로그램 종료
        }
    }

    // 원본 메시지를 서버로 전송하는 메서드
    private void sendRaw(String message) 
    {
        try {
            dos.writeUTF(message);
            dos.flush();
        } catch (IOException e) {
            System.out.println("메시지 전송 실패: " + e.getMessage());
        }
    }

    // 포맷된 메시지를 생성하여 전송하는 메서드
    public void send(String message) 
    {
        try {
            dos.writeUTF(message);
            dos.flush();
            
            // 현재 시간 가져오기
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String formattedMessage = "[" + chatName + "](" + timestamp + ") : " + message;
            System.out.println(formattedMessage);
            
        } catch (IOException e) {
            System.out.println("메시지 전송 실패: " + e.getMessage());
        }
    }

    public void receive(Scanner sc) 
    {
        new Thread(() -> {
            try {
                while (true) 
                {
                    String message = dis.readUTF();

                    if (message.startsWith("[이미지 전송]")) 
                    {
                        // 이미지 전송 알림 메시지 처리
                        System.out.println(message);
                        // 이미지 다운로드를 자동으로 요청하지 않고, 사용자가 직접 명령어를 입력하도록 안내
                        System.out.println("이미지를 다운로드하려면 '/getimg 이미지ID'를 입력하세요.");
                        System.out.print("> ");
                    } 
                    else if (message.startsWith("/imgdata ")) 
                    {
                        // 이미지 데이터 수신 처리
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2)
                        {
                            String imageId = parts[1].trim();
                            int size = dis.readInt(); // 서버가 int로 전송하므로 클라이언트도 int로 읽음
                            byte[] imageData = new byte[size];
                            dis.readFully(imageData);
                            // 이미지 저장
                            saveImage(imageId, imageData);
                        }
                    } 
                    else if (message.startsWith("닉네임이 변경되었습니다:")) 
                    {
                        // 닉네임 변경 메시지 처리
                        String newName = message.substring("닉네임이 변경되었습니다:".length()).trim();
                        if (!newName.isEmpty()) {
                            String oldName = this.chatName;
                            this.chatName = newName;
                            System.out.println("닉네임이 변경되었습니다: " + this.chatName);
                        }
                        System.out.print("> ");
                    }
                    else 
                    {
                        System.out.println(message);
                        System.out.print("> ");
                    }
                }
            } catch (IOException e) {
                if (!isQuitting) { // 이미 quit()이 호출된 경우 중복 호출 방지
                    System.out.println("서버와의 연결이 끊어졌습니다.");
                    quit();
                }
                // System.exit(0); // 필요시 주석 해제
            }
        }).start();
    }

    private void saveImage(String imageId, byte[] imageData) 
    {
        try {
            // 이미지 저장 디렉토리 설정
            String downloadsDir = "downloads";
            File dir = new File(downloadsDir);
            if (!dir.exists()) 
            {
                dir.mkdirs();
            }

            // 이미지 파일명 설정 (imageId를 파일명으로 사용, 확장자는 jpg로 고정)
            String fileName = "image_" + imageId + ".jpg";
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(imageData);
            fos.close();

            System.out.println("이미지를 다운로드 받았습니다: " + file.getAbsolutePath());
            System.out.print("> ");
        } catch (IOException e) {
            System.out.println("이미지 저장 실패: " + e.getMessage());
            System.out.print("> ");
        }
    }

    public void quit() 
    {
        if (isQuitting) return; // 이미 종료 중인 경우 중복 호출 방지
        isQuitting = true;
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[" + chatName + "] 채팅 서버 연결 종료");
        } catch (IOException e) {
            System.out.println("종료 중 오류 발생: " + e.getMessage());
        }
    }

    public static void main(String[] args) 
    {
        Scanner sc = new Scanner(System.in);

        // 서버 IP 입력 받기
        System.out.print("서버 IP를 입력하세요 (기본값: localhost): ");
        String serverIP = sc.nextLine().trim();
        if (serverIP.isEmpty()) 
        {
            serverIP = "localhost";
        }

        // 서버 포트 번호 입력 받기
        System.out.print("서버 포트 번호를 입력하세요 (기본값: 50005): ");
        String portInput = sc.nextLine().trim();
        int portNo = 50005; // 기본 포트 번호
        if (!portInput.isEmpty()) 
        {
            try 
            {
                portNo = Integer.parseInt(portInput);
            } 
            catch (NumberFormatException e) 
            {
                System.out.println("포트 번호가 유효하지 않습니다. 기본 포트 50005 사용.");
            }
        }

        // 대화명 입력 받기
        System.out.print("대화명을 입력하세요 : ");
        String chatName = sc.nextLine().trim();

        if (chatName.isEmpty()) 
        {
            System.out.println("대화명은 공백일 수 없습니다.");
            sc.close();
            return;
        }

        ChatClient chatClient = new ChatClient();
        chatClient.connect(serverIP, portNo, chatName, sc);
        chatClient.receive(sc);

        while (true)
        {
            System.out.print("> ");
            String message = sc.nextLine().trim();

            if (message.isEmpty()) continue;

            if (message.startsWith(chatClient.renameCommand)) 
            {
                chatClient.send(message);
            }
            else if (message.startsWith(chatClient.privateMessageCommand))
            {
                chatClient.send(message);
            } 
            else if (message.startsWith(chatClient.imageCommand)) 
            {
                String[] parts = message.split(" ", 2);
                if (parts.length == 2) 
                {
                    String filePath = parts[1].trim();
                    if (!filePath.isEmpty())
                    {
                        chatClient.sendFile(filePath);
                    } 
                    else
                    {
                        System.out.println("파일명을 입력해주세요.");
                    }
                } 
                else
                {
                    System.out.println("사용법: /img 전송할파일명");
                }
            }
            else if (message.startsWith(chatClient.getImageCommand)) 
            {
                // 이미지 다운로드 명령어 처리
                chatClient.send(message);
            } 
            else 
            {
                chatClient.send(message);
            }

            if (message.equals(chatClient.quitCommand)) break;
        }

        chatClient.quit();
        sc.close();
    }

    public void sendFile(String filePath) 
    {
        try {
            File file = new File(filePath);
            if (!file.exists()) 
            {
                System.out.println("파일이 존재하지 않습니다: " + filePath);
                return;
            }

            // 파일명 추출
            String fileName = file.getName();

            // /img 명령어 전송
            dos.writeUTF("/img " + fileName);
            dos.flush();

            // 파일 크기 전송 (int 타입으로 수정)
            int fileSize = (int) file.length();
            dos.writeInt(fileSize);
            dos.flush();

            // 파일 데이터를 전송
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1)
            {
                dos.write(buffer, 0, bytesRead);
            }
            fis.close();
            dos.flush();

            System.out.println("이미지 전송 완료: " + filePath);

        } catch (IOException e) {
            System.out.println("이미지 전송 실패: " + e.getMessage());
        }
    }
}
