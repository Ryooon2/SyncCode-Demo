package SyncCode.SyncCode_demo.domain.rtc;

import lombok.RequiredArgsConstructor;
import org.kurento.client.KurentoClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class KurentoRoomManager {
    private final ConcurrentHashMap<String, KurentoRoom> rooms = KurentoRoomMap.getInstance().getKurentoRooms();

    private final KurentoClient kurento;

    public KurentoRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(KurentoRoom room) {
        rooms.remove(room.getRoomId());

        room.close();
    }

    public KurentoRoom createRoom(String roomName) {
        KurentoRoom room = new KurentoRoom(kurento.createMediaPipeline(), roomName);
        rooms.put(room.getRoomId(), room);
        return room;
    }

    public List<KurentoRoom> getRooms(){
        return new ArrayList<>(rooms.values());
    }
}
