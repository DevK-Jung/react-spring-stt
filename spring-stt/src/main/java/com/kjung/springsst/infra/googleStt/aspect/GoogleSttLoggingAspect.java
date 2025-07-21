package com.kjung.springsst.infra.googleStt.aspect;

import com.kjung.springsst.infra.googleStt.GoogleSttHelper;
import com.kjung.springsst.infra.googleStt.vo.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * GoogleSttHelper 메서드 실행에 대한 AOP 로깅 Aspect
 * 메서드 실행 시간, 파일 정보, 결과 정보를 자동으로 로깅
 */
@Slf4j
@Aspect
@Component
public class GoogleSttLoggingAspect {

    /**
     * GoogleSttHelper 클래스의 모든 public 메서드에 대한 AOP 로깅을 수행합니다.
     * <p>
     * Speech-to-Text API 호출의 성능 모니터링과 디버깅을 위해 메서드 실행 전후의 상세 정보를 로깅합니다.
     * 처리 시간, 파일 정보, 성공/실패 여부를 추적하여 시스템 성능 분석과 오류 추적에 활용할 수 있습니다.
     * </p>
     * <p>
     * <strong>로깅 정보:</strong>
     * <ul>
     * <li>메서드 실행 시작/종료 시점</li>
     * <li>처리 대상 오디오 파일명</li>
     * <li>총 처리 시간 (밀리초 단위)</li>
     * <li>성공 시: 결과 정보, 실패 시: 오류 메시지</li>
     * </ul>
     * </p>
     *
     * @param joinPoint AOP 조인포인트 객체, 대상 메서드 정보와 실행 제어를 담당
     * @param file      처리할 오디오 파일
     * @return Object 대상 메서드의 원본 반환값을 그대로 전달
     * {@link TranscriptionResult} 또는 기타 메서드별 반환 타입
     * @throws Throwable 대상 메서드에서 발생한 모든 예외를 다시 던짐
     *                   <ul>
     *                   <li>{@link RuntimeException}: STT API 호출 실패, 파일 처리 오류 등</li>
     *                   <li>{@link IllegalArgumentException}: 잘못된 파라미터</li>
     *                   <li>기타 대상 메서드에서 발생 가능한 모든 예외</li>
     *                   </ul>
     * @apiNote 이 어드바이스는 {@code args(request)} 조건으로 인해 SttRequest를 첫 번째 파라미터로
     * 받는 메서드에만 적용됩니다. 다른 시그니처의 메서드는 별도의 어드바이스가 필요합니다.
     * @implNote <pre>
     * 로그 레벨:
     * - INFO: 시작/성공 로그
     * - ERROR: 실패 로그
     *
     * 포인트컷 패턴:
     * "execution(public * com.kjung.springsst.infra.googleStt.GoogleSttHelper.*(..)) && args(file, ..)"
     * </pre>
     * @see ProceedingJoinPoint
     * @see MultipartFile
     * @see GoogleSttHelper
     * @see Around
     * @since 1.0
     */
    @Around("execution(public * com.kjung.springsst.infra.googleStt.GoogleSttHelper.*(..)) && args(file, ..)")
    public Object logGoogleSttExecution(ProceedingJoinPoint joinPoint, MultipartFile file) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();
        String filename = file.getOriginalFilename();

        log.info("[STT API 시작] 메서드: {}, 파일: {}", methodName, filename);

        try {
            Object result = joinPoint.proceed();
            long processingTime = System.currentTimeMillis() - startTime;

            logSuccess(methodName, filename, processingTime, result);

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;

            log.error("[STT API 실패] 메서드: {}, 파일: {}, 처리시간: {}ms, 오류: {}",
                    methodName,
                    filename,
                    processingTime,
                    e.getMessage());

            throw e;
        }
    }

    /**
     * 성공 로그 출력.
     */
    private void logSuccess(String methodName, String filename, long processingTime, Object result) {
        if (result instanceof TranscriptionResult(String transcription, float averageConfidence)) {
            log.info("[STT API 완료] 메서드: {}, 파일: {}, 처리시간: {}ms, 텍스트길이: {}, 신뢰도: {}",
                    methodName,
                    filename,
                    processingTime,
                    transcription.length(),
                    averageConfidence);
        } else {
            log.info("[STT API 완료] 메서드: {}, 파일: {}, 처리시간: {}ms",
                    methodName, filename, processingTime);
        }
    }

}