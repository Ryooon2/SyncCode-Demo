package SyncCode.SyncCode_demo;

import SyncCode.SyncCode_demo.domain.dummy.UserData;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
public class SyncCodeDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SyncCodeDemoApplication.class, args);
	}

}
