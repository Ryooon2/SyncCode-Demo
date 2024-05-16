// conference.js

let ws;
let localStream;
let participants = {};
let roomId;
let userName;

// Initialize WebSocket connection and event handlers
function initWebSocket() {
    const dataContainer = document.getElementById('dataContainer');
    roomId = dataContainer.getAttribute('data-roomid');
    userName = dataContainer.getAttribute('data-username');

    ws = new WebSocket('ws://' + window.location.host + '/signal');

    ws.onopen = () => {
        console.log('WebSocket connection opened');
        joinRoom();
    };

    ws.onmessage = (message) => {
        const parsedMessage = JSON.parse(message.data);
        console.log('Received message: ' + message.data);

        switch (parsedMessage.id) {
            case 'existingParticipants':
                onExistingParticipants(parsedMessage.data);
                break;
            case 'newParticipantArrived':
                onNewParticipant(parsedMessage.name);
                break;
            case 'receiveSdpAnswer':
                receiveSdpAnswer(parsedMessage);
                break;
            case 'iceCandidate':
                addIceCandidate(parsedMessage);
                break;
            case 'participantLeft':
                onParticipantLeft(parsedMessage.name);
                break;
            default:
                console.error('Unrecognized message', parsedMessage);
        }
    };

    ws.onclose = () => {
        console.log('WebSocket connection closed');
    };
}

// Join the room
function joinRoom() {
    const message = {
        id: 'joinRoom',
        name: userName,
        room: roomId
    };
    sendMessage(message);
}

// Handle existing participants
function onExistingParticipants(data) {
    data.forEach(participant => {
        receiveVideo(participant);
    });

    // Start local video
    const video = document.getElementById('localVideo');
    const constraints = {
        audio: true,
        video: {
            width: 640,
            height: 480
        }
    };

    navigator.mediaDevices.getUserMedia(constraints)
        .then(stream => {
            localStream = stream;
            video.srcObject = stream;

            const options = {
                localVideo: video,
                mediaStream: stream,
                onicecandidate: onIceCandidate
            };
            participants[userName] = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options, function (error) {
                if (error) return console.error(error);
                this.generateOffer(onOffer);
            });
        })
        .catch(error => {
            console.error('Error accessing media devices.', error);
        });
}

// Handle new participant
function onNewParticipant(name) {
    if (!participants[name]) {
        console.log('New participant:', name);
        receiveVideo(name);
    }
}

// Receive video from a participant
function receiveVideo(name) {
    const video = document.createElement('video');
    video.id = name;
    video.autoplay = true;
    video.controls = false;
    document.getElementById('remoteVideos').appendChild(video);

    const options = {
        remoteVideo: video,
        onicecandidate: (candidate) => onRemoteIceCandidate(name, candidate)
    };

    participants[name] = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options, function (error) {
        if (error) return console.error('Error creating WebRtcPeer:', error);
        this.generateOffer((err, offerSdp) => {
            if (err) return console.error('Error generating offer:', err);
            sendMessage({
                id: 'receiveDataFrom',
                sender: name,
                sdpOffer: offerSdp
            });
        });
    });
}

// Handle SDP offer/answer
function onOffer(error, offerSdp) {
    if (error) return console.error('Error generating the offer', error);

    const message = {
        id: 'receiveDataFrom',
        sender: userName,
        sdpOffer: offerSdp
    };
    sendMessage(message);
}

function receiveSdpAnswer(message) {
    participants[message.name].rtcPeer.processAnswer(message.sdpAnswer, (error) => {
        if (error) return console.error('Error processing answer', error);
    });
}

// Handle ICE candidates
function onIceCandidate(candidate) {
    console.log('Local candidate:', JSON.stringify(candidate));
    sendMessage({
        id: 'onIceCandidate',
        candidate: candidate,
        name: userName
    });
}

function onRemoteIceCandidate(name, candidate) {
    console.log('Remote candidate:', JSON.stringify(candidate));
    sendMessage({
        id: 'onIceCandidate',
        candidate: candidate,
        name: name
    });
}

function addIceCandidate(message) {
    const candidate = new RTCIceCandidate({
        sdpMLineIndex: message.candidate.sdpMLineIndex,
        sdpMid: message.candidate.sdpMid,
        candidate: message.candidate.candidate
    });
    participants[message.name].rtcPeer.addIceCandidate(candidate, (error) => {
        if (error) console.error('Error adding candidate', error);
    });
}

// Handle participant leaving
function onParticipantLeft(name) {
    const participant = participants[name];
    if (participant) {
        participant.rtcPeer.dispose();
        const videoElement = document.getElementById(name);
        if (videoElement) {
            videoElement.parentNode.removeChild(videoElement);
        }
        delete participants[name];
    }
}

// Leave the room
function leaveRoom() {
    sendMessage({
        id: 'leaveRoom'
    });

    Object.keys(participants).forEach(name => {
        participants[name].rtcPeer.dispose();
    });
    participants = {};

    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }
    document.getElementById('localVideo').srcObject = null;

    document.getElementById('remoteVideos').innerHTML = '';

    if (ws) {
        ws.close();
    }
}

// Send message to server
function sendMessage(message) {
    const jsonMessage = JSON.stringify(message);
    console.log('Sending message: ' + jsonMessage);
    ws.send(jsonMessage);
}

// Initialize WebSocket when the page is loaded
window.addEventListener('load', initWebSocket);

window.onbeforeunload = function() {
    if (ws) {
        ws.close();
    }
};
