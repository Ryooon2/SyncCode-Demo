package SyncCode.SyncCode_demo.domain.rtc;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class KurentoUserSession implements Closeable {
    private final String name;
    private final String roomId;
    // Client 와 Spring 서버간 소켓 통신에 사용되는 인터페이스.
    private final WebSocketSession session;
    // pipeline이 미디어 플로우를 구별지음. 즉 같은 채팅방 -> pipeline이 같음. / 다른 채팅방 -> pipeline이 서로 다름.
    private final MediaPipeline pipeline;

    // 나의 webRtc 객체. 즉 업로드 링크.
    private final WebRtcEndpoint outgoingMedia;
    // 다른 사람들의 webRtc 객체, 즉 다운로드 링크들을 Map으로 보관.
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public KurentoUserSession(String name, String roomId, WebSocketSession session, MediaPipeline pipeline){
        this.name = name;
        this.roomId = roomId;
        this.session = session;
        this.pipeline = pipeline;

        // upload link interface 생성
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

        // IceCandidate 발견시 session을 이용해서 전파하는 eventListener 추가.
        this.outgoingMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                JsonObject response = new JsonObject();
                response.addProperty("id","iceCandidate");
                response.addProperty("name", name);
                response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));

                try{
                    // websocketSession은 여러 스레드에서 동시에 접근할 수 있으므로,
                    // 한번에 하나의 스레드만 메세지를 보낼 수 있도록 제한 해야함.
                    synchronized (session){
                        session.sendMessage(new TextMessage(response.toString()));
                    }
                }catch (IOException e){
                    // 나중에 따로 예외처리 할 예정
                    System.out.println(e);
                }
            }
        });

    }

    public WebRtcEndpoint getOutgoingMedia() {
        return outgoingMedia;
    }

    public ConcurrentHashMap<String, WebRtcEndpoint> getIncomingMedia(){
        return incomingMedia;
    }

    public String getName(){
        return name;
    }

    public String getRoomId(){
        return roomId;
    }

    public WebSocketSession getSession(){
        return session;
    }

    // sender는 미디어 스트림을 전송하는 사람.
    // sender 으로부터 데이터를 수신하는 webRtcEndpoint(IncomingMedia) 를
    // 자신의 sdpOffer 를 사용해 구축하는 메서드.
    public void receiveDataFrom(KurentoUserSession sender, String sdpOffer) throws IOException {
        final String spdAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);

        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", "receiveSdpAnswer");
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", spdAnswer);

        synchronized (session){
            session.sendMessage(new TextMessage(scParams.getAsString()));
        }
        this.getEndpointForUser(sender).gatherCandidates();
    }

    private WebRtcEndpoint getEndpointForUser(final KurentoUserSession sender){
        // 예외 처리는 나중에.
        // 만일 sender명의 현재 user명과 일치한다면, 즉 sdpOffer 제안을 보내는 쪽과 받는 쪽이 동일하다면?
        // loopback
        if(sender.getName().equals(name)){
            return outgoingMedia;
        }

        // sender 의 이름으로 나의 incomingMedia 에서 sender 의 webRtcEndpoint 객체를 가져옴.
        WebRtcEndpoint incoming = incomingMedia.get(sender.getName());

        // 만일 sender의 webRtCEndPoint 객체가 없다면
        if(incoming == null){
            // 새롭운 webRtcEndpoint 를 만들고, IceCandidateFoundListener를 추가함.
            incoming = new WebRtcEndpoint.Builder(pipeline).build();

            incoming.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
                @Override
                public void onEvent(IceCandidateFoundEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id","iceCandidate");
                    response.addProperty("name", name);
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));

                    try{
                        // websocketSession은 여러 스레드에서 동시에 접근할 수 있으므로,
                        // 한번에 하나의 스레드만 메세지를 보낼 수 있도록 제한 해야함.
                        synchronized (session){
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    }catch (IOException e){
                        // 나중에 따로 예외처리 할 예정
                        System.out.println(e);
                    }
                }
            });
            // incomingMedia 에 key: sender.name, value: incoming 으로 집어 넣음.
            incomingMedia.put(sender.getName(), incoming);
        }
        // sender 가 기존에 가지고 있던 webRtcEndpoint 와 새로 생성된 incoming 을 연결
        // n:m 연결을 위해 각 사용자는 다른 모든 사용자의 incomingMedia 맵에 자신의 outgoingMedia를 연결해야 함.
        sender.getOutgoingMedia().connect(incoming);

        return incoming;
    }

    public void cancelDataFrom(final KurentoUserSession sender) {this.cancelDataFrom(sender.getName());}

    public void cancelDataFrom(final String senderName){
        final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

        incoming.release(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                // 제대로 된 로깅은 나중에
                System.out.println("Release success, " + KurentoUserSession.this.name + ", " + senderName);
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                // 제대로 된 로깅, 예외처리는 나중에
                System.out.println("Error, " + KurentoUserSession.this.name + ", " + senderName);
            }
        });
    }

    @Override
    public void close() throws IOException {
        /*
        WebRtCEndpoint 들을 모두 release
         */

        // incomingMedia 들을 모두 release.
        for (final String remoteParticipantName : incomingMedia.keySet()){
            final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);

            ep.release(new Continuation<Void>() {
                @Override
                public void onSuccess(Void result) throws Exception {
                    // 제대로 된 로깅은 나중에
                    System.out.println("Release success, " + KurentoUserSession.this.name + ", " + remoteParticipantName);
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    // 제대로 된 로깅, 예외처리는 나중에
                    System.out.println("Error, " + KurentoUserSession.this.name + ", " + remoteParticipantName);
                }
            });
        }

        outgoingMedia.release(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                // 제대로 된 로깅은 나중에
                System.out.println("Release success, " + KurentoUserSession.this.name);
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                // 제대로 된 로깅, 예외처리는 나중에
                System.out.println("Error, " + KurentoUserSession.this.name);
            }
        });
    }

    public void sendMessage(JsonObject message) throws IOException {
        synchronized (session){
            session.sendMessage(new TextMessage(message.toString()));
        }
    }

    public void addCandidate(IceCandidate candidate, String name){
        // IceCandidate 가 자기 자신(현재 세션의 User)를 위한 것이라면, outgingMedia 에 candidate 를 추가
        // 사용자가 자신의 미디어 데이터를 외부로 보낼때 연결 경로를 최적화하는데 사용.
        if (this.name.compareTo(name) == 0) {
            outgoingMedia.addIceCandidate(candidate);
        } else {
            // IceCandidate 가 다른 사용자의 WebRtcEndpoint 와 관련이 있다면, 해당 사용자의 WebRtCEndpoint에 candidate 추가
            // 수신 중인 미디어 스트림의 연결 경로를 최적화하는데 사용.
            WebRtcEndpoint ep = incomingMedia.get(name);
            if (ep != null) {
                ep.addIceCandidate(candidate);
            }
        }
    }

    /*
    뭔지는 모르겠는데 docs에 있어서 추가.
    */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof KurentoUserSession)) {
            return false;
        }
        KurentoUserSession other = (KurentoUserSession) obj;
        boolean eq = name.equals(other.name);
        eq &= roomId.equals(other.roomId);
        return eq;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + name.hashCode();
        result = 31 * result + roomId.hashCode();
        return result;
    }

    // Test용
    public void loopback(String sdpOffer) throws IOException {
        final String sdpAnswer = this.outgoingMedia.processOffer(sdpOffer);

        this.outgoingMedia.connect(this.outgoingMedia);

        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", "receiveSdpAnswer");
        scParams.addProperty("name", this.name);
        scParams.addProperty("sdpAnswer", sdpAnswer);

        synchronized (session) {
            session.sendMessage(new TextMessage(scParams.toString()));
        }

        this.outgoingMedia.gatherCandidates();
    }


}
