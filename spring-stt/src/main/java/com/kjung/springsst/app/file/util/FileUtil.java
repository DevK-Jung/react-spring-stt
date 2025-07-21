package com.kjung.springsst.app.file.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FileUtil {
    /**
     * 파일 확장자 추출
     */
    public String getFileExtension(String filename) {
        if (filename == null) return "";

        int lastDotIndex = filename.lastIndexOf('.');

        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1)
            return "";

        return filename.substring(lastDotIndex + 1);
    }
}
