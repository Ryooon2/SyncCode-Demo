# SyncCode-Demo
## 개요
_주의 : 이 프로젝트는 webRtc 기술을 간단하게 적용해보기 위한 프로토타입에 불과합니다._

요구 사항:
- **JDK 17** : 이 프로젝트는 JDK 17을 사용하여 작성되었습니다. 따라서 JDK 17이 설치되어 있어야 합니다.
- **Gradle** : 프로젝트 빌드 및 종속성 관리를 위해 Gradle을 사용합니다.

프로젝트 목표 :
- SyncCode 프로젝트를 진행하기 앞서, 간단한 WebRtC 기술 적용. 
- KMS(Kurento Media Server)를 활용한 N:M 그툽 영상 통화.

사용 프레임워크 :
- Back-end : Spring Boot
- Front-end : Spring Thymeleaf

프로젝트 구성 요소들의 역할:
- Spring Server:
  - KMS와 client를 연결시켜주는 Signal Server의 역할
  - 뷰를 만들어주는 Front Server의 역할도 수행
- Kurento Media Server:
  - peer들간의 Media Stream을 중간에서 처리하는 역할.
  - 즉, A -> KMS -> B의 흐름으로 peer들간의 데이터가 전달.
- Conturn Server:
  - NAT 뒤에 있는 클라이언트의 public IP와 Port를 반환해주는 STUN server의 역할을 수행.
  - 동일한 NAT 환경에 클라이언트들이 존재할 경우, 혹은 엄격한 방화벽 환경일 경우 직접적인 P2P 연결이 불가능하다. 이러한 상황에서 중계 server의 역할을 수행할 TURN server로도 동작.

## 기본적인 동작 설명
이해를 돕기 위해, 구체적인 예시를 들어 설명을 하겠습니다.
### 상황 가정
어떠한 화상 채팅방에 A와 B라는 사람이 이미 참여하고 있는 와중에, C라는 사람이 새롭게 참가하는 상황.
### C 입장에서 발생하는 로직
1. C는 먼저 Spring Server의 웹소켓 endpoint, 즉 'ws://localhost:8080/signal'를 통해, 소켓 연결을 합니다.
2. C는 Spring Server에 id = 'joinRoom'인 json 메세지를 전송해, 특정 방에 참가 하고자함을 알립니다.
   1. 서버는 C에게 현재 방에 존재하는 기존 참가자들(A와 B)의 목록을 C에게 id = 'existingParticipants'인 Json 메세지를 전달합니다.
   2. 서버는 A와 B에게 C가 새로운 참가자임을 알리는 id = 'newParticipantArrived'인 Json 메세지를 전달합니다.
3. C는 2.1에서 받은 참가자 목록으로 KMS와 sdp 협상 과정을 거친 후, 각 참가자들에 대한 미디어 스트림을 수신하게 됩니다.

이때 sdp 협상 과정은 다음과 같습니다.
1. C는 연결할 상대(여기서는 A라고 가정하겠음)와 sdpOffer가 담긴 id = 'receiveDataFrom'인 Json 메세지를 Spring server에 전송합니다.
2. Spring server는 sdpOffer를 KMS에 전달하여 sdpAnswer를 받아오고, 이를 다시 C에게 전달합니다. 이를 통해, C와 KMS 사이의 webRtc 연결이 성립됩니다.
3. 또한 Spring server는 C의 webRtc endpoint와 A의 webRtc endpoint를 KMS를 통해 연결하여, A의 미디어 스트림이 C에게 전달되도록 설정합니다.

### 기존 참가자(A,B) 입장에서 발생하는 로직
1. A와 B는 C가 방에 새롭게 참가하면, id = 'newParticipantArrived'인 json 메세지를 수신합니다.
2. 이에 A와 B는 연결할 상대를 C로하고 sdpOffer가 담긴 id = 'receiveDataFrom'인 Json 메세지를 Spring server에 전송합니다.
3. 위에서 설명한 sdp 협상 과정과 같은 맥락으로 json 메세지가 처리되고, A와 B는 C의 미디어 스트림을 수신하게 됩니다.

## 시연 영상
![2024-05-23 13 14 23](https://github.com/Ryooon2/SyncCode-Demo/assets/138233569/273f28e7-84c1-4f61-88dc-fe7d1ac06b49)
원본: https://drive.google.com/file/d/1eyhIPzaeft7av6ykqqY-OZns0BEdnjyS/view?usp=sharing

위 시연 영상은 로컬 비디오 스트림을 왼쪽에, 해당 미디어 스트림을 KMS로 보내고 다시 자신에게 loopback한 영상을 오른쪽에 출력한 모습입니다.
원래의 목표대로 N:M 그룹 영상 통화를 시연 영상으로 남겼다면 더 좋았겠지만, 웹 환경에서 카메라 장치에 접근하려면 HTTPS를 사용해야만 합니다. 
이러한 제약 때문에, 시연 영상 및 테스트는 로컬 미디어 스트림을 KMS로 보내고, 이를 다시 loopback하는 방식으로 진행하였습니다.



## 프로젝트 구조
- SyncCode.SyncCode_demo.domain.rtc.KurentoHandler:
  - WebSocket 메세지를 처리하는 핸들러 클래스
- SyncCode.SyncCode_demo.domain.rtc.KurentoUserSession:
  - User 세션을 관리하고 WebRtc 연결을 설정하는 클래스
- SyncCode.SyncCode_demo.domain.rtc.KurentoRoom:
  - 채팅방을 관리하는 클래스
- SyncCode.SyncCode_demo.domain.rtc.KurentoRoomManager:
  - 채팅방을 생성하고 관리하는 클래스
- SyncCode.SyncCode_demo.domain.rtc.KurentoRoomMap:
  - 싱글톤으로 KurentoRoom 객체를 관리하는 클래스
- SyncCode.SyncCode_demo.domain.rtc.KurentoUserRegistry:
  - 현재 접속 중인 사용자를 추적하는 클래스

## Coturn Server 설치 방법
참고 : https://doc-kurento.readthedocs.io/en/latest/user/faq.html#faq-coturn-install

_AWS EC2 ubuntu 기준으로 작성_
```bash
sudo apt-get update
sudo apt-get install --no-install-recommends coturn -y
sudo vim /etc/turnserver.conf
#------------------------------------------------------------------------------------------------
# <CoturnIp>, <TurnUser>, <TurnPassword>를 적절한 값으로 바꿔서
# /etc/turnserver.conf에 아래 내용을 추가.
# <CoturnIp>는 EC2 public IP이여야 합니다.
external-ip=<CoturnIp>

listening-port=3478

min-port=49152
max-port=65535

fingerprint

lt-cred-mech

user=<TurnUser>:<TurnPassword>

realm=kurento.org

log-file=/var/log/turn.log

simple-log
#------------------------------------------------------------------------------------------------
sudo install -o turnserver -g turnserver -m 644 /dev/null /var/log/turn.log
sudo vim /etc/default/coturn      # TURNSERVER_ENABLED=1이 되도록 수정
sudo service coturn restart
```
_Conturn server가 설치된 EC2 머신은 3478 (UDP/TCP)과 49152-65535 (UDP/TCP) 포트를 열어두어야 합니다._

위 설치과정이 전부 끝나면, https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/  로 가서, 정상 작동하는지 테스트를 해야합니다.
stun:<CoturnIp>:3478

turn:<CoturnIp>:3478 (TURN 사용자 이름과 비밀번호 포함)로 설정하고 Gather candidates 클릭합니다.

STUN 테스트의 경우 srflx 유형의 후보가 표시되고, TURN 테스트의 경우 srflx 및 relay 유형의 후보가 표시되는지 확인하시길 바랍니다.

## Kurento Server 설치 방법
참고 : https://doc-kurento.readthedocs.io/en/latest/user/installation.html#running

_AWS EC2 ubuntu 기준으로 작성_
```bash
sudo apt-get update
sudo apt install docker.io -y
sudo usermod -aG docker $USER # 명령어 실행후, EC2 기계 재접속 필요
docker pull kurento/kurento-media-server:7.0.0
docker run -d --name kurento --network host  kurento/kurento-media-server:7.0.0
```
_KMS EC2 머신은 8888 (TCP) 포트를 열어두어야 합니다_

위 과정이 끝나면 기본적인 KMS의 설치가 완료됩니다.

하지만 본 프로젝트를 정상적으로 실행하려면, KMS에 위에서 설치한 Conturn server를 연동시켜야 합니다.

따라서 다음과 같이 KMS 설정 파일을 수정해주시기 바랍니다.
```bash
docker exec -it kurento bash
nano /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini   # KMS 설정 파일 수정

# ---------------------------------------------------------------------------
# ;로 주석처리된 곳을 다음과 같이 변경
stunServerAddress=<CoturnIp>
stunServerPort=3478
turnURL=<TurnUser>:<TurnPassword>@<CoturnIp>:3478
# ---------------------------------------------------------------------------
exit
docker restart kurento
```

## 프로젝트 실행 시 주의사항
1. application.properties를 SyncCode_demo/src/main/resources 위치에 다음과 같이 작성해야합니다.
```properties
spring.application.name=SyncCode_demo

kurento.client.websocket.url=ws://<your-KMS-IP>>:8888/kurento
```

2. SyncCode_demo/src/main/resources/static/js 디렉토리의 conference.js 혹은 loopback-test.js 에 설정된 STUN/TURN 서버의 IP를 자신의 Conturn 서버 IP로 설정하셔야 합니다.
```js
// conference.js의 STUN/TURN 서버 설정 부분
function createPeerConnection(name) {
    const peer = new RTCPeerConnection({
        iceServers: [
            { urls: 'stun:<your-Coturn-ip>>:3478' },
            { urls: 'turn:<your-Conturn-ip>>:3478', username: '<username>>', credential: '<credential>>' }]
    });
    // ... 이하 생략
}

// loopback-test.js의 STUN/TURN 서버 설정 부분
function joinRoom() {
    // ... 생략
        peerConnection = new RTCPeerConnection({
            iceServers: [
                { urls: 'stun:<your-Coturn-ip>>:3478' },
                { urls: 'turn:<your-Conturn-ip>>:3478', username: '<username>>', credential: '<credential>>' }]
        });

    // ... 생략
```
3. video-loopback.html과 loopback-test.js는 자신의 비디오 스트림을 KMS로부터 다시 loopback하는, test용 front-end 코드입니다.
```JAVA
// SyncCode_demo/src/main/java/SyncCode/SyncCode_demo/domain/controller/MainController.java
// ... 생략
@GetMapping("/joinRoom")
public String joinRoom(HttpServletRequest request, Model model, @RequestParam(name = "roomId") String roomId) {
        HttpSession session = request.getSession(false);
        String userName = (String) session.getAttribute("userName");
        model.addAttribute("userName", userName);
        model.addAttribute("roomId", roomId);
        return "video-loopback"; // 이부분을 return "video"로 바꾸시면 n:m 그룹 영상통화가 실행됩니다.
        }
```
