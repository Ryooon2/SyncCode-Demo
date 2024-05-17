$(document).ready(function() {
    let localVideo = document.getElementById('localVideo');
    let remoteVideo = document.getElementById('remoteVideo');
    const dataContainer = document.getElementById('dataContainer');
    const roomId = dataContainer.getAttribute('data-roomid');
    const userName = dataContainer.getAttribute('data-username');

    let localStream;
    let peerConnection;

    const ws = new WebSocket('ws://' + window.location.host + '/signal'); // 서버 주소를 올바르게 설정하세요.

    ws.onopen = function() {
        console.log('WebSocket connection opened');
        joinRoom();
    };

    ws.onmessage = function(message) {
        const parsedMessage = JSON.parse(message.data);
        console.log('Received message:', parsedMessage);
        switch (parsedMessage.id) {
            case 'iceCandidate':
                console.log('Received ICE candidate:', parsedMessage.candidate);
                peerConnection.addIceCandidate(new RTCIceCandidate(parsedMessage.candidate))
                    .then(() => console.log('ICE candidate added successfully.'))
                    .catch(e => console.error('Error adding ICE candidate:', e));
                break;
            case 'receiveSdpAnswer':
                console.log('Received SDP answer:', parsedMessage.sdpAnswer);
                peerConnection.setRemoteDescription(new RTCSessionDescription({
                    type: 'answer',
                    sdp: parsedMessage.sdpAnswer
                }))
                    .then(() => console.log('Remote description set successfully.'))
                    .catch(e => console.error('Error setting remote description:', e));
                break;
            case 'existingParticipants':
                // Handle the existing participants message
                onExistingParticipants(parsedMessage.data);
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
            console.log('Local stream set to localVideo');

            peerConnection = new RTCPeerConnection({
                iceServers: [
                    { urls: 'stun:43.201.67.51:3478' },
                    { urls: 'turn:43.201.67.51:3478', username: 'root', credential: 'root' }]
            });

            localStream.getTracks().forEach(track => {
                peerConnection.addTrack(track, localStream);
            });

            peerConnection.onicecandidate = function(event) {
                if (event.candidate) {
                    console.log('Sending ICE candidate:', event.candidate);
                    ws.send(JSON.stringify({
                        id: 'onIceCandidate',
                        candidate: event.candidate,
                        name: userName
                    }));
                } else {
                    console.log('All ICE candidates have been sent');
                }
            };

            peerConnection.ontrack = function(event) {
                console.log('Remote track received:', event.streams[0]);
                if (event.streams && event.streams[0]) {
                    remoteVideo.srcObject = event.streams[0];
                    console.log('remoteVideo.srcObject set:', remoteVideo.srcObject);
                } else {
                    console.error('No remote stream received');
                }
            };

            peerConnection.onconnectionstatechange = function(event) {
                console.log('Connection state change:', peerConnection.connectionState);
                switch (peerConnection.connectionState) {
                    case 'connected':
                        console.log('The connection has become fully connected');
                        break;
                    case 'disconnected':
                        console.log('The connection has been disconnected');
                        break;
                    case 'failed':
                        console.log('The connection has failed');
                        break;
                    case 'closed':
                        console.log('The connection has been closed');
                        break;
                }
            };

            peerConnection.oniceconnectionstatechange = function(event) {
                console.log('ICE connection state change:', peerConnection.iceConnectionState);
                switch (peerConnection.iceConnectionState) {
                    case 'new':
                        console.log('ICE connection state is new');
                        break;
                    case 'checking':
                        console.log('ICE connection state is checking');
                        break;
                    case 'connected':
                        console.log('ICE connection state is connected');
                        break;
                    case 'completed':
                        console.log('ICE connection state is completed');
                        break;
                    case 'disconnected':
                        console.log('ICE connection state is disconnected');
                        break;
                    case 'failed':
                        console.log('ICE connection state is failed');
                        break;
                    case 'closed':
                        console.log('ICE connection state is closed');
                        break;
                }
            };


            peerConnection.createOffer().then(offer => {
                return peerConnection.setLocalDescription(offer);
            }).then(() => {
                console.log('Sending SDP offer:', peerConnection.localDescription.sdp);
                ws.send(JSON.stringify({
                    id: 'loopback',
                    room: roomId,
                    name: userName,
                    sdpOffer: peerConnection.localDescription.sdp
                }));
            }).catch(console.error);

        }).catch(console.error);
    }

    function onExistingParticipants(participants) {
        // Handle the existing participants (if necessary)
        console.log('Existing participants:', participants);
        // For loopback test, you may not need to do anything with this message
    }

    window.onbeforeunload = function() {
        ws.close();
    };
});
