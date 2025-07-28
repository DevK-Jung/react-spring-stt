import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

const SttStreaming = () => {
  const [isStreaming, setIsStreaming] = useState(false);
  const [transcription, setTranscription] = useState<string>('');
  const [error, setError] = useState<string>('');
  const [connectionStatus, setConnectionStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');

  const wsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    return () => {
      stopStreaming();
    };
  }, []);

  const connectWebSocket = () => {
    try {
      setConnectionStatus('connecting');
        const wsUrl = `ws://localhost:8099/ws/speech`;
      console.log('Connecting to WebSocket:', wsUrl);
      wsRef.current = new WebSocket(wsUrl);

      wsRef.current.onopen = () => {
        setConnectionStatus('connected');
        setError('');
      };

      wsRef.current.onmessage = (event) => {
        console.log('Received WebSocket message:', event.data);
        try {
          const data = JSON.parse(event.data);
          console.log('Parsed data:', data);
          
          if (data.error) {
            setError(data.error);
            return;
          }
          
          if (data.transcript) {
            if (data.isFinal) {
              setTranscription(prev => prev + data.transcript + ' ');
            } else {
              setTranscription(prev => {
                const lines = prev.trim().split('\n');
                if (lines[lines.length - 1].startsWith('[임시]')) {
                  lines[lines.length - 1] = '[임시] ' + data.transcript;
                } else {
                  lines.push('[임시] ' + data.transcript);
                }
                return lines.join('\n');
              });
            }
          }
        } catch (err) {
          console.error('메시지 파싱 오류:', err);
          setError('응답 데이터 파싱 오류');
        }
      };

      wsRef.current.onerror = (error) => {
        console.error('WebSocket error:', error);
        setError('WebSocket 연결 오류가 발생했습니다.');
        setConnectionStatus('disconnected');
      };

      wsRef.current.onclose = (event) => {
        console.log('WebSocket connection closed:', event.code, event.reason);
        setConnectionStatus('disconnected');
      };

    } catch (err) {
      setError('WebSocket 연결 실패: ' + (err instanceof Error ? err.message : String(err)));
      setConnectionStatus('disconnected');
    }
  };

  const startStreaming = async () => {
    try {
      if (connectionStatus !== 'connected') {
        connectWebSocket();
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      });

      streamRef.current = stream;

      // AudioContext를 사용하여 PCM 데이터 처리
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({
        sampleRate: 16000
      });


      audioContextRef.current = audioContext;

      const source = audioContext.createMediaStreamSource(stream);
      const processor = audioContext.createScriptProcessor(4096, 1, 1);
      processorRef.current = processor;

      processor.onaudioprocess = (event) => {
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
          const inputBuffer = event.inputBuffer;
          const inputData = inputBuffer.getChannelData(0);
          
          // Float32Array를 Int16Array로 변환 (LINEAR16)
          const int16Data = new Int16Array(inputData.length);
          for (let i = 0; i < inputData.length; i++) {
            int16Data[i] = Math.max(-32768, Math.min(32767, Math.floor(inputData[i] * 32768)));
          }
          
          // ArrayBuffer로 변환하여 전송
          const buffer = int16Data.buffer;
          console.log('Sending PCM audio data:', buffer.byteLength, 'bytes');
          wsRef.current.send(buffer);
        }
      };

      source.connect(processor);
      processor.connect(audioContext.destination);

      // START_STREAM 신호 전송
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        console.log('Sending START_STREAM message');
        wsRef.current.send('START_STREAM');
      }

      setIsStreaming(true);
      setError('');
      setTranscription('');

    } catch (err) {
      setError('마이크 접근 권한이 필요합니다: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const stopStreaming = () => {
    // END_STREAM 신호 전송
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      console.log('Sending END_STREAM message');
      wsRef.current.send('END_STREAM');
    }

    if (processorRef.current) {
      processorRef.current.disconnect();
      processorRef.current = null;
    }

    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }

    setIsStreaming(false);
  };

  const clearTranscription = () => {
    setTranscription('');
  };

  const getConnectionStatusColor = () => {
    switch (connectionStatus) {
      case 'connected':
        return '#28a745';
      case 'connecting':
        return '#ffc107';
      case 'disconnected':
        return '#dc3545';
      default:
        return '#6c757d';
    }
  };

  const getConnectionStatusText = () => {
    switch (connectionStatus) {
      case 'connected':
        return '연결됨';
      case 'connecting':
        return '연결 중...';
      case 'disconnected':
        return '연결 끊김';
      default:
        return '알 수 없음';
    }
  };

  return (
    <div style={{padding: '20px', maxWidth: '800px', margin: '0 auto'}}>
      <div style={{marginBottom: '20px'}}>
        <Link to="/" style={{textDecoration: 'none', color: '#007bff'}}>
          ← 메인으로 돌아가기
        </Link>
      </div>

      <h1>STT 스트리밍</h1>
      <p>실시간으로 음성을 텍스트로 변환합니다. WebSocket을 통해 연속적인 음성 인식이 가능합니다.</p>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>연결 상태</h3>
        <div style={{marginBottom: '20px'}}>
          <span style={{fontWeight: 'bold'}}>WebSocket 상태: </span>
          <span style={{
            color: getConnectionStatusColor(),
            fontWeight: 'bold'
          }}>
            {getConnectionStatusText()}
          </span>
        </div>

        {connectionStatus !== 'connected' && (
          <button
            onClick={connectWebSocket}
            disabled={connectionStatus === 'connecting'}
            style={{
              padding: '10px 20px',
              backgroundColor: connectionStatus === 'connecting' ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: connectionStatus === 'connecting' ? 'not-allowed' : 'pointer',
              fontSize: '16px'
            }}
          >
            {connectionStatus === 'connecting' ? '연결 중...' : 'WebSocket 연결'}
          </button>
        )}
      </div>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>스트리밍 제어</h3>
        <div style={{marginBottom: '20px', display: 'flex', gap: '10px'}}>
          <button
            onClick={isStreaming ? stopStreaming : startStreaming}
            disabled={connectionStatus !== 'connected'}
            style={{
              padding: '12px 24px',
              backgroundColor: connectionStatus !== 'connected' ? '#6c757d' : (isStreaming ? '#dc3545' : '#28a745'),
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: connectionStatus !== 'connected' ? 'not-allowed' : 'pointer',
              fontSize: '16px'
            }}
          >
            {isStreaming ? '스트리밍 중지' : '스트리밍 시작'}
          </button>

          <button
            onClick={clearTranscription}
            style={{
              padding: '12px 24px',
              backgroundColor: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '16px'
            }}
          >
            텍스트 지우기
          </button>
        </div>

        {isStreaming && (
          <div style={{
            color: '#dc3545',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <span style={{
              width: '10px',
              height: '10px',
              backgroundColor: '#dc3545',
              borderRadius: '50%',
              animation: 'blink 1s infinite'
            }}></span>
            실시간 음성 인식 중...
          </div>
        )}
      </div>

      {error && (
        <div style={{
          padding: '15px',
          backgroundColor: '#f8d7da',
          border: '1px solid #f5c6cb',
          borderRadius: '4px',
          color: '#721c24',
          marginBottom: '20px'
        }}>
          {error}
        </div>
      )}

      <div style={{
        padding: '20px',
        border: '1px solid #ddd',
        borderRadius: '4px',
        backgroundColor: '#f8f9fa',
        minHeight: '200px'
      }}>
        <h4 style={{margin: '0 0 15px 0'}}>실시간 변환 결과:</h4>
        <div style={{
          padding: '15px',
          backgroundColor: 'white',
          border: '1px solid #e9ecef',
          borderRadius: '4px',
          minHeight: '150px',
          fontSize: '16px',
          lineHeight: '1.5',
          whiteSpace: 'pre-wrap',
          fontFamily: 'system-ui, -apple-system, sans-serif',
          maxHeight: '400px',
          overflowY: 'auto'
        }}>
          {transcription || '음성 인식 결과가 여기에 표시됩니다...'}
        </div>
      </div>

      <style>{`
        @keyframes blink {
          0%, 50% { opacity: 1; }
          51%, 100% { opacity: 0; }
        }
      `}</style>
    </div>
  );
};

export default SttStreaming;