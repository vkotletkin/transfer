package com.example.testtikalucene.service;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tomcat.util.http.fileupload.util.mime.MimeUtility;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class Testcode {
    public static void main(String[] args) throws Exception {
        Path outputDir = Paths.get("extracted_files");
        Files.createDirectories(outputDir);

        AutoDetectParser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);

        // Обработчик метаданных (игнорируем текст тела, чтобы не забивать память)
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1)
        );

        ParseContext context = new ParseContext();

        // КЛЮЧЕВОЙ МОМЕНТ: Установка кастомного экстрактора
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true; // Разрешаем парсить (и извлекать) все вложения
            }

            @Override
            public void parseEmbedded(InputStream stream, org.xml.sax.ContentHandler handler, Metadata metadata, boolean outputHtml) {
                String rawName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                String fileName = "unknown_" + UUID.randomUUID().toString();

                if (rawName != null && !rawName.isEmpty()) {
                    try {
                        // 1. Декодируем RFC 2047 (из =?utf-8?B?...?= в нормальный текст)
                        fileName = MimeUtility.decodeText(rawName);
                    } catch (Exception e) {
                        fileName = rawName; // Если не удалось декодировать, оставляем как есть
                    }

                    // 2. Очищаем имя от символов, запрещенных в Windows (\ / : * ? " < > |)
                    // Также убираем управляющие символы и лишние пробелы
                    fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
                }

                try {
                    // Теперь Path.resolve не упадет
                    Path outputPath = outputDir.resolve(fileName);

                    // Проверка: если файл с таким именем уже есть, добавим префикс, чтобы не перезаписать
                    if (Files.exists(outputPath)) {
                        outputPath = outputDir.resolve(System.currentTimeMillis() + "_" + fileName);
                    }

                    try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = stream.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        System.out.println("Сохранен файл: " + outputPath.toAbsolutePath());
                    }
                    System.out.println();
                } catch (Exception e) {
                    System.err.println("Ошибка при сохранении файла " + fileName + ": " + e.getMessage());
                }
            }
        });

        try (InputStream is = new FileInputStream("email-attachment.msg")) {
            wrapper.parse(is, handler, new Metadata(), context);
        }

        // После завершения у вас в handler.getMetadataList() будут все метаданные,
        // а файлы уже будут лежать на диске.

        System.out.println();
    }
}
