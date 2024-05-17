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

            const peer = createPeerConnection(userName);
            stream.getTracks().forEach(track => peer.addTrack(track, stream));
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

    const peer = createPeerConnection(name);
    participants[name] = { peer, video };
}

function createPeerConnection(name) {
    const peer = new RTCPeerConnection({
        iceServers: [
            { urls: 'stun:43.201.67.51:3478' },
            { urls: 'turn:43.201.67.51:3478', username: 'root', credential: 'root' }]
    });

    peer.onicecandidate = (event) => {
        if (event.candidate) {
            sendMessage({
                id: 'onIceCandidate',
                candidate: event.candidate,
                name: userName
            });
        }
    };

    peer.ontrack = (event) => {
        const videoElement = document.getElementById(name);
        if (videoElement) {
            videoElement.srcObject = event.streams[0];
        }
    };

    if (name !== userName) {
        peer.createOffer()
            .then(offer => peer.setLocalDescription(offer))
            .then(() => {
                sendMessage({
                    id: 'receiveDataFrom',
                    sender: userName,
                    sdpOffer: peer.localDescription
                });
            })
            .catch(error => console.error('Error creating offer', error));
    }

    return peer;
}

// Handle SDP offer/answer
function receiveSdpAnswer(message) {
    const peer = participants[message.name].peer;
    const remoteDesc = new RTCSessionDescription(message.sdpAnswer);
    peer.setRemoteDescription(remoteDesc).catch(error => console.error('Error setting remote description', error));
}

// Handle ICE candidates
function addIceCandidate(message) {
    const candidate = new RTCIceCandidate(message.candidate);
    const peer = participants[message.name].peer;
    peer.addIceCandidate(candidate).catch(error => console.error('Error adding candidate', error));
}

// Handle participant leaving
function onParticipantLeft(name) {
    const participant = participants[name];
    if (participant) {
        participant.peer.close();
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
        participants[name].peer.close();
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
