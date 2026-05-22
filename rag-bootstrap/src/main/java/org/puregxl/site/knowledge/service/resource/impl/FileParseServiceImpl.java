package org.puregxl.site.knowledge.service.resource.impl;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.puregxl.site.knowledge.service.resource.FileParseService;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class FileParseServiceImpl implements FileParseService {
    private Tika tika = new Tika();

    @Autowired
    private KnowledgeStorageResourceService storageResourceService;


    @Override
    public String parseFileByTika(MultipartFile file) throws TikaException, IOException {
        return parseFile(file);
    }

    @Override
    public String parseFileByTika(String fileurl) throws TikaException, IOException {
        MultipartFile file = storageResourceService.downloadDocumentAsMultipartFile(fileurl);

        return parseFile(file);
    }

    public String parseFile(MultipartFile file) throws TikaException, IOException {
        String text;
        try (InputStream inputStream = file.getInputStream()) {
            text = tika.parseToString(inputStream);
        }
        return text;
    }
}
