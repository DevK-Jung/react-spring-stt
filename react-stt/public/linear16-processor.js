// public/linear16-processor.js
class Linear16Processor extends AudioWorkletProcessor {
  process(inputs, outputs, parameters) {
    const input = inputs[0];

    if (input && input.length > 0) {
      const channelData = input[0];

      // Float32Array를 Int16Array로 변환
      const int16Buffer = new Int16Array(channelData.length);

      for (let i = 0; i < channelData.length; i++) {
        // -1.0 ~ 1.0 범위를 -32768 ~ 32767 범위로 변환
        const s = Math.max(-1, Math.min(1, channelData[i]));
        int16Buffer[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
      }

      // 변환된 데이터를 메인 스레드로 전송
      this.port.postMessage(int16Buffer.buffer);
    }

    // true를 반환하여 프로세서가 계속 실행되도록 함
    return true;
  }
}

// 프로세서 등록
registerProcessor('linear16-processor', Linear16Processor);