import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Akıllı Tahta Yönetim Sunucusu (Java WebSocket)
 * Derlemek için: javac -cp "Java-WebSocket-1.5.3.jar:slf4j-api-1.7.36.jar" SmartBoardServer.java
 * Çalıştırmak için: java -cp ".:Java-WebSocket-1.5.3.jar:slf4j-api-1.7.36.jar" SmartBoardServer
 */
public class SmartBoardServer extends WebSocketServer {

    // Sisteme bağlı olan tahtaların (client) listesi. Thread-Safe yapı.
    // Key: Board ID (Örn: "10-A"), Value: WebSocket objesi
    private ConcurrentHashMap<String, WebSocket> connectedBoards = new ConcurrentHashMap<>();
    
    // Yöneticilerin (Adminlerin) listesi. Birden fazla müdür yardımcısı bağlanabilir.
    private ConcurrentHashMap<WebSocket, Boolean> connectedAdmins = new ConcurrentHashMap<>();

    // Tahtaların son bilinen durumları (JSON formatında yansıtmak için basit cache)
    private ConcurrentHashMap<String, String> boardStatuses = new ConcurrentHashMap<>();

    public SmartBoardServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Yeni bağlantı talebi geldi: " + conn.getRemoteSocketAddress());
        // Bağlantı açıldığında kimlik doğrulama mesajı beklenir (onMessage kısmında)
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Eğer kopan bağlantı bir Admin ise
        if (connectedAdmins.containsKey(conn)) {
            connectedAdmins.remove(conn);
            System.out.println("Yönetici (Admin) bağlantısı koptu.");
        } else {
            // Kopan bağlantı bir Tahta ise
            String disconnectedBoardId = null;
            for (Map.Entry<String, WebSocket> entry : connectedBoards.entrySet()) {
                if (entry.getValue().equals(conn)) {
                    disconnectedBoardId = entry.getKey();
                    break;
                }
            }
            if (disconnectedBoardId != null) {
                connectedBoards.remove(disconnectedBoardId);
                boardStatuses.remove(disconnectedBoardId);
                System.out.println("Tahta bağlantısı koptu: " + disconnectedBoardId);
                broadcastBoardListToAdmins(); // Yöneticilerin ekranını güncelle
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Gelen Mesaj: " + message);
        
        // Gelen JSON verisini basit String operasyonlarıyla çözümlüyoruz (Harici bağımlılık azaltmak için)
        // Gerçek kurumsal bir projede (6000+ satırlık aşamada) buraya GSON veya Jackson kütüphanesi entegre edilmelidir.
        try {
            if (message.contains("\"type\":\"auth\"")) {
                handleAuthentication(conn, message);
            } else if (message.contains("\"type\":\"command\"")) {
                handleAdminCommand(conn, message);
            } else if (message.contains("\"type\":\"status_update\"")) {
                handleBoardStatusUpdate(conn, message);
            } else if (message.contains("\"type\":\"announcement\"")) {
                handleAnnouncement(conn, message);
            }
        } catch (Exception e) {
            System.err.println("Mesaj işlenirken hata oluştu: " + e.getMessage());
        }
    }

    // 1. Kimlik Doğrulama: Bağlanan kişi Admin mi, yoksa Tahta mı?
    private void handleAuthentication(WebSocket conn, String json) {
        if (json.contains("\"role\":\"admin\"")) {
            connectedAdmins.put(conn, true);
            System.out.println("Yeni bir Admin sisteme giriş yaptı.");
            broadcastBoardListToAdmins(); // Admine mevcut tahtaları gönder
        } else if (json.contains("\"role\":\"board\"")) {
            // JSON içinden board_id çıkarma (Örn: {"type":"auth", "role":"board", "board_id":"11-C"})
            String boardId = extractJsonValue(json, "board_id");
            if (boardId != null) {
                connectedBoards.put(boardId, conn);
                boardStatuses.put(boardId, "locked"); // Varsayılan durum
                System.out.println("Tahta kaydedildi: Sınıf " + boardId);
                broadcastBoardListToAdmins();
            }
        }
    }

    // 2. Yöneticiden Gelen Komutları Tahtalara İletme
    private void handleAdminCommand(WebSocket conn, String json) {
        if (!connectedAdmins.containsKey(conn)) return; // Sadece adminler komut verebilir

        String target = extractJsonValue(json, "target");
        String command = extractJsonValue(json, "command");

        System.out.println("Admin Komutu: Hedef=" + target + ", Komut=" + command);

        String payload = "{\"type\":\"command\", \"action\":\"" + command + "\"}";

        if ("all".equals(target) || "lock_all".equals(command) || "unlock_all".equals(command)) {
            // Tüm tahtalara gönder
            for (WebSocket boardConn : connectedBoards.values()) {
                boardConn.send(payload);
            }
        } else if (target != null && connectedBoards.containsKey(target)) {
            // Belirli bir tahtaya gönder
            connectedBoards.get(target).send(payload);
        }
    }

    // 3. Tahtalardan Gelen Durum Güncellemelerini Adminlere Yansıtma
    private void handleBoardStatusUpdate(WebSocket conn, String json) {
        String boardId = extractJsonValue(json, "board_id");
        String status = extractJsonValue(json, "status");
        
        if (boardId != null && status != null) {
            boardStatuses.put(boardId, status);
            broadcastBoardListToAdmins();
        }
    }

    // 4. Tüm Tahtalara Duyuru Gönderme
    private void handleAnnouncement(WebSocket conn, String json) {
        if (!connectedAdmins.containsKey(conn)) return;
        String message = extractJsonValue(json, "message");
        
        if(message != null) {
            String payload = "{\"type\":\"announcement\", \"text\":\"" + message + "\"}";
            for (WebSocket boardConn : connectedBoards.values()) {
                boardConn.send(payload);
            }
            System.out.println("Duyuru yayınlandı: " + message);
        }
    }

    // Yöneticilerin panelindeki listeyi güncelleyen metod
    private void broadcastBoardListToAdmins() {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"type\":\"board_list\", \"boards\":{");
        
        String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        boolean first = true;
        
        for (Map.Entry<String, String> entry : boardStatuses.entrySet()) {
            if (!first) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(entry.getKey()).append("\":{");
            jsonBuilder.append("\"status\":\"").append(entry.getValue()).append("\",");
            jsonBuilder.append("\"last_seen\":\"").append(timeNow).append("\"}");
            first = false;
        }
        jsonBuilder.append("}}");
        
        String response = jsonBuilder.toString();
        for (WebSocket adminConn : connectedAdmins.keySet()) {
            try {
                adminConn.send(response);
            } catch (Exception e) {
                // Ignore dead connections
            }
        }
    }

    // Yardımcı Metod: Basit JSON ayrıştırıcı (Harici kütüphane gereksinimini kaldırmak için)
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;
        return json.substring(startIndex, endIndex);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Sunucu Hatası: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("=== AKILLI TAHTA SUNUCUSU BAŞLATILDI ===");
        System.out.println("Port: " + getPort());
        System.out.println("İstemciler (Tahtalar ve Adminler) bağlantı kurabilir.");
    }

    public static void main(String[] args) {
        int port = 8887; // Varsayılan port
        SmartBoardServer server = new SmartBoardServer(port);
        server.setReuseAddr(true);
        server.start();
    }
}
