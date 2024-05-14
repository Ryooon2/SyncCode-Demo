package SyncCode.SyncCode_demo.domain.rtc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class KurentoUserRegistry {
    /*
    현재 접속중인 사용자를 추적하는데 사용하는 클래스.
     */
    private final ConcurrentHashMap<String, KurentoUserSession> usersByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KurentoUserSession> usersBySessionId = new ConcurrentHashMap<>();

    public void register(KurentoUserSession user) {
        usersByName.put(user.getName(), user);
        usersBySessionId.put(user.getSession().getId(), user);
    }

    public KurentoUserSession getByName(String name) {
        return usersByName.get(name);
    }

    public KurentoUserSession getBySession(WebSocketSession session){
        return usersBySessionId.get(session.getId());
    }

    public boolean exists(String name){
        return usersByName.keySet().contains(name);
    }

    public KurentoUserSession removeBySession(WebSocketSession session) {
        final KurentoUserSession user = getBySession(session);
        usersByName.remove(user.getName());
        usersBySessionId.remove(session.getId());
        return user;
    }
}
