package SyncCode.SyncCode_demo.domain.dummy;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class UserData {
    private static UserData userData = new UserData();

    // <userName, psw>
    private ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
    private UserData() {
        data.put("aa", "aa");
        data.put("bb", "bb");
    }

    public static UserData getInstance(){
        return userData;
    }
}
