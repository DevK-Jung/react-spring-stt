import { useRef, useState } from 'react';

const SttStreaming_2 = () => {
  // 최종 확정된 텍스트를 저장할 상태. 문장 단위로 추가됩니다.
  const [finalVoiceText, setFinalVoiceText] = useState<string>('');
  // 현재 말하고 있는 중인 잠정적인 텍스트를 저장할 상태. 계속 업데이트됩니다.
  const [interimVoiceText, setInterimVoiceText] = useState<string>('');

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
    // 연결 종료 시 텍스트 상태 초기화
    // setFinalVoiceText('');
    setInterimVoiceText('');
  };

  const setupWebSocket = async () => {
    closeWebSocket(); // 기존 연결이 있다면 닫고 상태 초기화

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
        // '/linear16-processor.js' 파일이 웹 서버의 루트 경로에 올바르게 위치하는지 확인하세요.
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
          // 평균 볼륨 계산 (간단한 방법)
          const avgVolume = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length;

          if (avgVolume > 30) { // 임계값 30은 환경에 따라 조절 필요
            setIsTalking(true);
          } else {
            setIsTalking(false);
          }

          // 다음 프레임에서 함수 다시 호출
          requestAnimationFrame(detectTalking);
        };

        detectTalking(); // 음성 활동 감지 시작

        // MediaRecorder 정지 핸들러 (AudioWorkletNode를 사용하므로 MediaRecorder는 사실상 오디오 데이터 전송에는 사용되지 않음)
        // 그러나 스트림을 관리하고 정리하는 용도로 유지
        mediaRecorder.current.onstop = () => {
          if (processor.current && audioContext.current && source.current && stream.current) {
            stream.current.getTracks().forEach((track) => track.stop()); // 스트림의 모든 트랙 중지
            source.current.disconnect(processor.current); // 오디오 노드 연결 해제
            processor.current.disconnect(audioContext.current.destination);
          }
        };

        mediaRecorder.current.start(chunkRate); // MediaRecorder 시작 (AudioWorklet 사용 시 오디오 데이터 자체는 여기서 오지 않음)
        setIsListening(true);
      } catch (error) {
        console.error('마이크 접근 오류:', error);
        alert('마이크 접근에 실패했습니다. 브라우저 설정을 확인해주세요.');
      }
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        // 백엔드에서 보낸 JSON 데이터 파싱: { transcript: "...", isFinal: true/false }
        const data: { transcript: string; isFinal: boolean } = JSON.parse(event.data);

        if (data.isFinal) {
          // 최종 결과인 경우:
          // 1. 최종 텍스트에 현재 최종 결과를 추가합니다. (뒤에 공백 추가)
          setFinalVoiceText((prev) => prev + data.transcript + ' ');
          // 2. 잠정 텍스트는 비웁니다.
          setInterimVoiceText('');
        } else {
          // 잠정 결과인 경우:
          // 1. 잠정 텍스트만 업데이트합니다. 최종 텍스트는 건드리지 않습니다.
          setInterimVoiceText(data.transcript);
        }
      } catch (error) {
        console.error('메시지 파싱 오류:', error);
      }
    };

    ws.onerror = (error: Event) => {
      console.error('WebSocket 오류:', error);
      setFinalVoiceText('');
      setInterimVoiceText('');
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
      setFinalVoiceText('');
      setInterimVoiceText('');
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
            {/* 최종 확정된 텍스트와 현재 잠정적인 텍스트를 함께 표시합니다. */}
            {finalVoiceText}
            {/* 잠정 텍스트는 최종 텍스트와 시각적으로 구분되도록 스타일을 적용할 수 있습니다. */}
            <span style={{color: 'gray'}}>{interimVoiceText}</span>
            {/* 아무 텍스트도 없을 때만 기본 메시지 표시 */}
            {!finalVoiceText && !interimVoiceText && '음성을 입력해주세요...'}
          </div>
        </div>
      </header>
    </div>
  );
};

export default SttStreaming_2;