$(document).ready(function() {
    const localVideo = document.getElementById('localVideo');
    const remoteVideo = document.getElementById('remoteVideo');
    const dataContainer = document.getElementById('dataContainer');
    const roomId = dataContainer.getAttribute('data-roomid');
    const userName = dataContainer.getAttribute('data-username');

    let localStream;
    let peerConnection;

    const ws = new WebSocket('ws://localhost:8080/signal'); // 서버 주소를 올바르게 설정하세요.

    ws.onopen = function() {
        joinRoom();
    };

    ws.onmessage = function(message) {
        const parsedMessage = JSON.parse(message.data);
        switch (parsedMessage.id) {
            case 'iceCandidate':
                peerConnection.addIceCandidate(new RTCIceCandidate(parsedMessage.candidate));
                break;
            case 'receiveSdpAnswer':
                peerConnection.setRemoteDescription(new RTCSessionDescription({
                    type: 'answer',
                    sdp: parsedMessage.sdpAnswer
                }));
                break;
            default:
                console.error('Unrecognized message', parsedMessage);
        }
    };

    function joinRoom() {
        navigator.mediaDevices.getUserMedia({
            video: true,
            audio: true
        }).then(stream => {
            localStream = stream;
            localVideo.srcObject = localStream;

            peerConnection = new RTCPeerConnection({
                iceServers: [{urls: 'stun:stun.l.google.com:19302'}]
            });

            localStream.getTracks().forEach(track => {
                peerConnection.addTrack(track, localStream);
            });

            peerConnection.onicecandidate = function(event) {
                if (event.candidate) {
                    ws.send(JSON.stringify({
                        id: 'onIceCandidate',
                        candidate: event.candidate,
                        name: userName
                    }));
                }
            };

            peerConnection.ontrack = function(event) {
                remoteVideo.srcObject = event.streams[0];
            };

            peerConnection.createOffer().then(offer => {
                return peerConnection.setLocalDescription(offer);
            }).then(() => {
                ws.send(JSON.stringify({
                    id: 'loopback',
                    room: roomId,
                    name: userName,
                    sdpOffer: peerConnection.localDescription.sdp
                }));
            }).catch(console.error);

        }).catch(console.error);
    }

    window.onbeforeunload = function() {
        ws.close();
    };
});
