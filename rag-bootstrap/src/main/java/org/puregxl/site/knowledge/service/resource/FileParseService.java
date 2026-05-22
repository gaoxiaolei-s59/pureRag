package org.puregxl.site.knowledge.service.resource;

import org.apache.tika.exception.TikaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileParseService {
    /**
     * 文件解析
     * @param file
     * @return
     */
    String parseFileByTika(MultipartFile file) throws TikaException, IOException;


    /**
     *
     * @param fileurl
     * @return
     */
    String parseFileByTika(String fileurl) throws TikaException, IOException;
}
