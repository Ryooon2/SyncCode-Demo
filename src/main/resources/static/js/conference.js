var ws;
var participants = {};

window.onbeforeunload = function() {
    if (ws) {
        ws.close();
    }
};

$(document).ready(function() {
    var roomId = $('#dataContainer').data('roomid');
    var userName = $('#dataContainer').data('username');
    getLocalStream(userName, roomId);
});

function getLocalStream(userName, roomId) {
    navigator.mediaDevices.getUserMedia({ audio: true, video: true })
        .then(stream => {
            document.getElementById('localVideo').srcObject = stream; // 로컬 비디오에 스트림 연결
            connectWebSocket(userName, roomId, stream);
        }).catch(error => {
        console.error("Error accessing media devices.", error);
    });
}

function connectWebSocket(userName, roomId, stream) {
    ws = new WebSocket('ws://' + window.location.host + '/signal');

    ws.onopen = function() {
        sendMessage({
            id: 'joinRoom',
            name: userName,
            room: roomId
        });
    };

    ws.onmessage = function(message) {
        var parsedMessage = JSON.parse(message.data);
        console.info('Received message:', parsedMessage.id);

        switch (parsedMessage.id) {
            case 'existingParticipants':
                onExistingParticipants(parsedMessage, userName, stream);
                break;
            case 'newParticipantArrived':
                onNewParticipant(parsedMessage);
                break;
            case 'participantLeft':
                onParticipantLeft(parsedMessage);
                break;
            case 'receiveVideoAnswer':
                participants[parsedMessage.name].rtcPeer.processAnswer(parsedMessage.sdpAnswer);
                break;
            case 'iceCandidate':
                participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate);
                break;
            default:
                console.error('Unrecognized message', parsedMessage);
        }
    };
}

function onExistingParticipants(msg, userName, stream) {
    var video = document.getElementById('localVideo');
    var options = {
        localVideo: video,
        videoStream: stream,
        mediaConstraints: { audio: true, video: true },
        onicecandidate: onLocalIceCandidate,
        configuration: {
            iceServers: [{urls: 'stun:stun.l.google.com:19302'}]
        }
    };

    var rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options, function(offerErr) {
        if (offerErr) return console.error('Error creating WebRtcPeer:', offerErr);
        this.generateOffer((error, offerSdp) => {
            if (error) return console.error('Error generating offer:', error);
            sendMessage({
                id: "receiveDataFrom",
                sender: userName,
                sdpOffer: offerSdp
            });
        });
    });

    participants[userName] = {
        name: userName,
        rtcPeer: rtcPeer
    };

    msg.data.forEach(name => {
        receiveVideo(name);
    });
}

function onNewParticipant(parsedMessage) {
    var name = parsedMessage.name;
    if (!participants[name]) {
        console.log('New participant:', name);
        receiveVideo(name);
    }
}


function receiveVideo(name) {
    var video = document.createElement('video');
    video.autoplay = true;
    video.controls = false;
    document.getElementById('remoteVideos').appendChild(video);

    var options = {
        remoteVideo: video,
        onicecandidate: onRemoteIceCandidate.bind(null, name),
        configuration: { iceServers: [{urls: 'stun:stun.l.google.com:19302'}] }
    };

    var rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options, function(error) {
        if (error) return console.error('Error creating WebRtcPeer:', error);
        this.generateOffer((err, offerSdp) => {
            if (err) return console.error('Error generating offer:', err);
            sendMessage({
                id: "receiveDataFrom",
                sender: name,
                sdpOffer: offerSdp
            });
        });
    });

    participants[name] = {
        name: name,
        rtcPeer: rtcPeer
    };
}

function onParticipantLeft(request) {
    var participant = participants[request.name];
    if (participant) {
        participant.rtcPeer.dispose();
        var videoElement = document.getElementById('video-' + request.name);
        if (videoElement) {
            videoElement.parentNode.removeChild(videoElement);
        }
        delete participants[request.name];
    }
}

function sendMessage(message) {
    var jsonMessage = JSON.stringify(message);
    console.log('Sending message:', jsonMessage);
    ws.send(jsonMessage);
}

function onLocalIceCandidate(candidate) {
    console.log("Local candidate:", JSON.stringify(candidate));
    sendMessage({
        id: 'onIceCandidate',
        candidate: candidate,
        name: localName
    });
}

function onRemoteIceCandidate(name, candidate) {
    console.log("Remote candidate:", JSON.stringify(candidate));
    sendMessage({
        id: 'onIceCandidate',
        candidate: candidate,
        name: name
    });
}

function leaveRoom() {
    sendMessage({
        id: 'leaveRoom'
    });

    // 로컬 비디오 스트림 정지
    const localVideo = document.getElementById('localVideo');
    if (localVideo.srcObject) {
        localVideo.srcObject.getTracks().forEach(track => track.stop());
    }
    localVideo.srcObject = null;

    // 모든 원격 비디오 스트림 정지 및 삭제
    document.getElementById('remoteVideos').innerHTML = '';

    // WebSocket 연결 종료
    if (ws) {
        ws.close();
    }
}