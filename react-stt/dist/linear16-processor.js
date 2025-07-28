class Linear16Processor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buffer = [];
    this.bufferSize = 4096; // 청크 크기
  }

  process(inputs, outputs, parameters) {
    const input = inputs[0];
    
    if (input && input.length > 0) {
      const inputChannel = input[0];
      
      // Float32 배열을 Int16으로 변환
      for (let i = 0; i < inputChannel.length; i++) {
        // -1.0 ~ 1.0 범위의 float를 -32768 ~ 32767 범위의 int16으로 변환
        const int16Value = Math.max(-32768, Math.min(32767, Math.floor(inputChannel[i] * 32768)));
        this.buffer.push(int16Value);
      }

      // 버퍼가 충분히 찰 때까지 기다렸다가 전송
      if (this.buffer.length >= this.bufferSize) {
        const int16Array = new Int16Array(this.buffer.splice(0, this.bufferSize));
        
        // 메인 스레드로 데이터 전송
        this.port.postMessage(int16Array.buffer);
      }
    }

    return true; // 계속 처리하도록 true 반환
  }
}

registerProcessor('linear16-processor', Linear16Processor);