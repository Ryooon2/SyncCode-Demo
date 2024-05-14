package SyncCode.SyncCode_demo.domain.rtc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.RequiredArgsConstructor;
import org.kurento.client.Continuation;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class KurentoRoom implements Closeable {
    private final MediaPipeline pipeline;

    private final String roomName;

    private final String roomId = UUID.randomUUID().toString();

    private int userCount = 0;

    private ConcurrentHashMap<String, KurentoUserSession> participants = new ConcurrentHashMap<>();

    public String getRoomId(){
        return roomId;
    }

    public String getRoomName(){
        return roomName;
    }

    public int getUserCount() {
        return userCount;
    }

    @PreDestroy
    private void shutdown(){
        this.close();
    }

    public Collection<KurentoUserSession> getParticipants(){
        return participants.values();
    }

    public KurentoUserSession getParticipant(String name){
        return participants.get(name);
    }

    public KurentoUserSession join(String userName, WebSocketSession session) throws IOException{
        // 새로운 참여자의 KurentoUserSession 을 생성
        final KurentoUserSession participant = new KurentoUserSession(userName, this.roomId, session, this.pipeline);

        // 기존 참여자에게 새로운 참여자가 발생했음을 알림.
        joinRoom(participant);

        // 참여자 Map 에 새로운 참여자 추가
        participants.put(participant.getName(), participant);

        // 새로운 참여자에게 기존 참여자의 명단을 전송.
        sendParticipantNames(participant);

        userCount = participants.size();
        return participant;
    }

    public void leave(KurentoUserSession user) throws IOException {
        this.removeParticipant(user.getName());
        userCount = participants.size();
        user.close();
    }

    public Collection<String> joinRoom(KurentoUserSession newParticipant) throws IOException {
        /*
        새로운 참여자가 발생시, 기존 참여자들에게 새로운 참여자가 나타났다는 메세지를 전송.
         */
        // 유저가 참여했음을 알리는 JsonObject
        final JsonObject newParticipantMsg = new JsonObject();
        newParticipantMsg.addProperty("id", "newParticipantArrived");
        newParticipantMsg.addProperty("name", newParticipant.getName());

        final List<String> participantsList = new ArrayList<>(participants.values().size());

        // 모든 참여자에게 sendMessage
        for (final KurentoUserSession participant : participants.values()) {
            try{
                participant.sendMessage(newParticipantMsg);
            } catch (final IOException e){
                // 예외처리는 나중에
                System.out.println(e);
            }
            participantsList.add(participant.getName());
        }
        return participantsList;
    }

    public void removeParticipant(String name) throws IOException {
        /*
        참여자가 나갈 경우 다음과 같은 로직들을 실행.
        1. 참여자 Map 에서 해당 User 를 제거.
        2. 다른 모든 참여자들에게
            2.1 이탈자에 대한 webRtcEndpoint 를 release 하도록 함.
            2.2 이탈자가 발생했다는 JsonObject 를 전송.
         */
        participants.remove(name);
        userCount = participants.size();

        // 2번 로직이 실패한 참여자들을 모으기 위한 List
        final List<String> unNotifiedParticipants = new ArrayList<>();
        // 참여자 이탈 메세지를 위한 JsonObject
        final JsonObject participantLeftJson = new JsonObject();

        participantLeftJson.addProperty("id", "participantLeft");
        participantLeftJson.addProperty("name", name);

        // 다른 모든 참여자들에게
        for (final KurentoUserSession participant : participants.values()) {
            try {
                // 이탈자에 대한 webRtcEndpoint 를 release 시킴.
                participant.cancelDataFrom(name);
                // 이탈자가 발생했다는 JsonObject 를 전송.
                participant.sendMessage(participantLeftJson);
            } catch (final IOException e) {
                unNotifiedParticipants.add(participant.getName());
            }
        }

        if (!unNotifiedParticipants.isEmpty()) {
            // 오류제어를 위한 코드가 필요한데, 일단은 스킵
            System.out.println(unNotifiedParticipants);
        }
    }

    public void sendParticipantNames(KurentoUserSession user) throws IOException {
        /*
        새로운 참여자 = user
        user 에게 기존 참여중인 참여자의 이름을 JsonObject 로 전달.
         */
        final JsonArray participantsArray = new JsonArray();
        for (final KurentoUserSession participant : this.getParticipants()) {
            if (!participant.equals(user)) {
                final JsonElement participantName = new JsonPrimitive(participant.getName());
                participantsArray.add(participantName);
            }
        }

        final JsonObject existingParticipantsMsg = new JsonObject();
        existingParticipantsMsg.addProperty("id", "existingParticipants");
        existingParticipantsMsg.add("data", participantsArray);
        user.sendMessage(existingParticipantsMsg);
    }

    @Override
    public void close()  {
        for (final KurentoUserSession user : participants.values()) {
            try {
                user.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        participants.clear();

        pipeline.release(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                System.out.println(roomId + ": Pipeline released");
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                System.out.println(roomId + ": Could not release Pipeline");
            }
        });
    }
}
