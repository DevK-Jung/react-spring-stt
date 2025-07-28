import { useRef, useState } from 'react';

const SttStreaming_2 = () => {
  const [voiceText, setVoiceText] = useState<string>('');
  const [isTalking, setIsTalking] = useState<boolean>(false);
  const [isListening, setIsListening] = useState<boolean>(false);

  const webSocket = useRef<WebSocket | null>(null);
  const mediaRecorder = useRef<MediaRecorder | null>(null);
  const audioContext = useRef<AudioContext | null>(null);
  const processor = useRef<AudioWorkletNode | null>(null);
  const audioChunks = useRef<Uint8Array[]>([]);
  const stream = useRef<MediaStream | null>(null);
  const source = useRef<MediaStreamAudioSourceNode | null>(null);
  const analyser = useRef<AnalyserNode | null>(null);

  const closeWebSocket = () => {
    if (webSocket.current) {
      webSocket.current.close();
      webSocket.current = null;
    }
    setIsListening(false);
  };

  const setupWebSocket = async () => {
    closeWebSocket();

    const ws = new WebSocket('ws://localhost:8099/ws/speech');

    ws.onopen = async () => {
      try {
        const sampleRate = 16000;
        const chunkRate = 100;

        // 마이크 권한 요청 및 스트림 생성
        stream.current = await navigator.mediaDevices.getUserMedia({
          audio: {
            sampleRate: sampleRate,
            channelCount: 1,
            echoCancellation: true,
          },
        });

        mediaRecorder.current = new MediaRecorder(stream.current);

        // AudioContext 생성
        audioContext.current = new (window.AudioContext || (window as any).webkitAudioContext)({
          sampleRate: sampleRate,
        });

        // AudioWorklet 모듈 로드
        await audioContext.current.audioWorklet.addModule('/linear16-processor.js');

        // 오디오 소스 생성
        source.current = audioContext.current.createMediaStreamSource(stream.current);

        // AudioWorkletNode 생성
        processor.current = new AudioWorkletNode(
          audioContext.current,
          'linear16-processor'
        );

        // 변환된 오디오 데이터를 웹소켓으로 전송
        processor.current.port.onmessage = (event: MessageEvent) => {
          if (webSocket.current && webSocket.current.readyState === WebSocket.OPEN) {
            webSocket.current.send(event.data);
            audioChunks.current.push(new Int16Array(event.data) as unknown as Uint8Array);
          }
        };

        // 음성 활동 감지를 위한 Analyser 설정
        analyser.current = audioContext.current.createAnalyser();
        analyser.current.fftSize = 256;
        const dataArray = new Uint8Array(analyser.current.frequencyBinCount);

        // 오디오 노드 연결
        source.current.connect(processor.current);
        processor.current.connect(audioContext.current.destination);
        source.current.connect(analyser.current);

        // 음성 활동 감지 함수
        const detectTalking = () => {
          if (!webSocket.current || !analyser.current) {
            return;
          }

          analyser.current.getByteFrequencyData(dataArray);
          const avgVolume = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length;

          if (avgVolume > 30) {
            setIsTalking(true);
          } else {
            setIsTalking(false);
          }

          requestAnimationFrame(detectTalking);
        };

        detectTalking();

        // MediaRecorder 정지 핸들러
        mediaRecorder.current.onstop = () => {
          if (processor.current && audioContext.current && source.current && stream.current) {
            stream.current.getTracks().forEach((track) => track.stop());
            source.current.disconnect(processor.current);
            processor.current.disconnect(audioContext.current.destination);
          }
        };

        mediaRecorder.current.start(chunkRate);
        setIsListening(true);
      } catch (error) {
        console.error('마이크 접근 오류:', error);
        alert('마이크 접근에 실패했습니다. 브라우저 설정을 확인해주세요.');
      }
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        // const data: WebSocketMessage = JSON.parse(event.data);
        const data: any = JSON.parse(event.data);
        console.log(data);
        setVoiceText((prev) => prev + ' ' + data.transcript);
      } catch (error) {
        console.error('메시지 파싱 오류:', error);
      }
    };

    ws.onerror = (error: Event) => {
      console.error('WebSocket 오류:', error);
      setVoiceText('');
    };

    ws.onclose = () => {
      console.log('WebSocket 연결 종료');

      // 리소스 정리
      if (processor.current) {
        processor.current.disconnect();
        processor.current = null;
      }
      if (audioContext.current) {
        audioContext.current.close();
        audioContext.current = null;
      }
      if (mediaRecorder.current) {
        mediaRecorder.current.stop();
        mediaRecorder.current = null;
      }
      if (stream.current) {
        stream.current.getTracks().forEach(track => track.stop());
        stream.current = null;
      }

      setIsListening(false);
      setIsTalking(false);
    };

    webSocket.current = ws;
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>실시간 음성 인식 (STT)</h1>

        <div className="controls">
          <button
            onClick={setupWebSocket}
            disabled={isListening}
            className="btn btn-start"
          >
            {isListening ? '듣는 중...' : '듣기 시작'}
          </button>

          <button
            onClick={closeWebSocket}
            disabled={!isListening}
            className="btn btn-stop"
          >
            멈추기
          </button>
        </div>

        <div className="status">
          {isListening && (
            <div className={`status-indicator ${isTalking ? 'talking' : ''}`}>
              {isTalking ? '🎤 말하는 중...' : '🔇 대기 중...'}
            </div>
          )}
        </div>

        <div className="transcript">
          <h2>인식된 텍스트:</h2>
          <div className="transcript-text">
            {voiceText || '음성을 입력해주세요...'}
          </div>
        </div>
      </header>
    </div>
  );
};

export default SttStreaming_2;