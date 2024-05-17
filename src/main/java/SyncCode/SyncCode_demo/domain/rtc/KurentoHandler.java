package SyncCode.SyncCode_demo.domain.rtc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kurento.client.IceCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

//@Component
@RequiredArgsConstructor
public class KurentoHandler extends TextWebSocketHandler {
    // 데이터를 Json 으로 주고 받기 때문에, GSON을 통해 직렬화, 역직렬화를 함.
    private static final Gson gson = new GsonBuilder().create();

    private final KurentoUserRegistry registry;

    private final KurentoRoomManager roomManager;


    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (!(message instanceof TextMessage)) {
            System.out.println("Unknown WebSocket Message Type: " + message.getClass());
            return;
        }
        // message 를 TextMessage 로 업캐스팅
        TextMessage textMessage = (TextMessage) message;

        final JsonObject jsonMessage = gson.fromJson(textMessage.getPayload(), JsonObject.class);
        final KurentoUserSession user = registry.getBySession(session);

        switch (jsonMessage.get("id").getAsString()) {
            case "joinRoom":
                joinRoom(jsonMessage, session);
                break;

            case "receiveDataFrom":
                final String senderName = jsonMessage.get("sender").getAsString();
                final KurentoUserSession sender = registry.getByName(senderName);
                final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
                user.receiveDataFrom(sender, sdpOffer);
                break;

            case "leaveRoom":
                leaveRoom(user);
                break;

            case "onIceCandidate":
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

                if (user != null) {
                    IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(cand, jsonMessage.get("name").getAsString());
                }
                break;

            // test 용으로 loopback 만듬.
            case "loopback":
                joinRoom(jsonMessage, session);
                registry.getBySession(session).loopback(jsonMessage.get("sdpOffer").getAsString());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 유저의 연결이 끊어진 경우, 참여자 목록에서 유저 제거
        KurentoUserSession user = registry.removeBySession(session);
    }

    private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
        final String roomId = params.get("room").getAsString();
        final String name = params.get("name").getAsString();
        System.out.println(roomId+name);

        KurentoRoom room = roomManager.getRoom(roomId);

        final KurentoUserSession user = room.join(name, session);

        registry.register(user);
    }

    private void leaveRoom(KurentoUserSession user) throws IOException {
        final KurentoRoom room = roomManager.getRoom(user.getRoomId());
        room.leave(user);
    }
}
