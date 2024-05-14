package SyncCode.SyncCode_demo.domain.rtc;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;

/*
싱글 톤으로 생성.
KurentoRoom 들을 보관하는 Map
 */
@Getter
@Setter
public class KurentoRoomMap {
    private static KurentoRoomMap kurentoRoomMap = new KurentoRoomMap();
    private ConcurrentHashMap<String, KurentoRoom> kurentoRooms = new ConcurrentHashMap<>();

    private KurentoRoomMap(){
        // 더미 데이터.

    }

    public static KurentoRoomMap getInstance() {
        return kurentoRoomMap;
    }
}
