// lib/main.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:socket_io_client/socket_io_client.dart' as IO;

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  State createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _channel = const MethodChannel('media_projection');
  RTCPeerConnection? pc;
  MediaStream? localStream;
  MediaStream? screenStream;
  final localRenderer = RTCVideoRenderer();
  final remoteRenderer = RTCVideoRenderer();
  IO.Socket? socket;

  final String signalingUrl = 'http://10.0.2.2:5000'; // change to your server URL (use device IP for real device)
  String token = ''; // fill in via your admin panel token generation
  String roomId = ''; // fill room id

  @override
  void initState() {
    super.initState();
    initRenderers();
  }

  @override
  void dispose() {
    localRenderer.dispose();
    remoteRenderer.dispose();
    pc?.close();
    socket?.disconnect();
    super.dispose();
  }

  Future<void> initRenderers() async {
    await localRenderer.initialize();
    await remoteRenderer.initialize();
  }

  Future<void> startClient() async {
    // 1) connect to signaling
    socket = IO.io(signalingUrl, IO.OptionBuilder()
        .setTransports(['websocket'])
        .setAuth({'token': token})
        .build());

    socket!.onConnect((_) => print('connected to signaling'));
    socket!.on('offer', (data) async {
      await pc?.setRemoteDescription(RTCSessionDescription(data['sdp']['sdp'], data['sdp']['type']));
      final answer = await pc!.createAnswer();
      await pc!.setLocalDescription(answer);
      socket!.emit('answer', {'sdp': answer});
    });
    socket!.on('answer', (data) async {
      await pc?.setRemoteDescription(RTCSessionDescription(data['sdp']['sdp'], data['sdp']['type']));
    });
    socket!.on('ice-candidate', (data) async {
      final c = data['candidate'];
      await pc?.addCandidate(RTCIceCandidate(c['candidate'], c['sdpMid'], c['sdpMLineIndex']));
    });
    socket!.on('user-joined', (_) async {
      final offer = await pc!.createOffer();
      await pc!.setLocalDescription(offer);
      socket!.emit('offer', {'sdp': offer});
    });

    // 2) create PC & get camera
    pc = await createPeerConnection({
      'iceServers': [
        {'urls': 'stun:stun.l.google.com:19302'}
      ]
    });

    pc!.onIceCandidate = (candidate) {
      if (candidate != null) {
        socket!.emit('ice-candidate', {'candidate': candidate.toMap()});
      }
    };

    pc!.onTrack = (event) {
      if (event.streams.isNotEmpty) {
        remoteRenderer.srcObject = event.streams[0];
      }
    };

    // get camera + mic
    localStream = await navigator.mediaDevices.getUserMedia({'audio': true, 'video': true});
    localRenderer.srcObject = localStream;
    localStream!.getTracks().forEach((track) => pc!.addTrack(track, localStream!));
  }

  Future<void> requestProjectionAndStartService() async {
    try {
      // 1) request media projection permission via native
      await _channel.invokeMethod('requestProjection');
      // 2) start foreground service so MediaProjection can be used (android requirement)
      await _channel.invokeMethod('startForegroundService');
      print('Projection requested and foreground service started');
    } on PlatformException catch (e) {
      print('PlatformException when requesting projection: $e');
      return;
    }
  }

  Future<void> startScreenShare() async {
    if (pc == null) {
      await startClient();
    }

    // Ask native to show projection permission & start foreground service
    await requestProjectionAndStartService();

    // Now call getDisplayMedia - flutter_webrtc will attempt to use existing projection permission
    try {
      screenStream = await navigator.mediaDevices.getDisplayMedia({'video': true});
      final screenTrack = screenStream!.getVideoTracks()[0];

      // find existing video sender
      final sender = pc!.getSenders().firstWhere((s) => s.track?.kind == 'video', orElse: () => null);
      if (sender == null) {
        pc!.addTrack(screenTrack, screenStream!);
      } else {
        await sender.replaceTrack(screenTrack);
      }

      // update local preview to show screen
      localRenderer.srcObject = screenStream;

      // renegotiate (create offer)
      final offer = await pc!.createOffer();
      await pc!.setLocalDescription(offer);
      socket!.emit('offer', {'sdp': offer});
      print('Screen sharing started');

      // listen for stop
      screenTrack.onEnded = () async {
        print('Screen track ended, reverting to camera');
        // revert
        final camTrack = localStream!.getVideoTracks()[0];
        final sender = pc!.getSenders().firstWhere((s) => s.track?.kind == 'video', orElse: () => null);
        if (sender != null) await sender.replaceTrack(camTrack);
        localRenderer.srcObject = localStream;
        screenStream = null;

        final offer = await pc!.createOffer();
        await pc!.setLocalDescription(offer);
        socket!.emit('offer', {'sdp': offer});
      };
    } catch (e) {
      print('getDisplayMedia failed: $e');
    }
  }

  Future<void> stopForegroundService() async {
    try {
      await _channel.invokeMethod('stopForegroundService');
    } catch (e) {
      print('stopForegroundService failed: $e');
    }
  }

  Widget buildControls() {
    return Column(
      children: [
        ElevatedButton(onPressed: () => startClient(), child: Text('Start camera & connect')),
        ElevatedButton(onPressed: () => startScreenShare(), child: Text('Start screen share')),
        ElevatedButton(onPressed: () async {
          await stopForegroundService();
          print('Stopped service');
        }, child: Text('Stop foreground service'))
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter WebRTC ScreenShare',
      home: Scaffold(
        appBar: AppBar(title: Text('Screen share test')),
        body: Column(
          children: [
            Expanded(
              child: RTCVideoView(localRenderer, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover),
            ),
            Expanded(
              child: RTCVideoView(remoteRenderer, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover),
            ),
            buildControls(),
          ],
        ),
      ),
    );
  }
}
