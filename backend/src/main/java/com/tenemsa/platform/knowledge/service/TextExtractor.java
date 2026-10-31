package com.tenemsa.platform.knowledge.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Turns an uploaded file into plain text. Supported: txt, md, csv, log, json, pdf, docx. Scanned PDFs (images) have no text. */
@Component
public class TextExtractor {

    private static final Set<String> PLAIN = Set.of("txt", "md", "markdown", "csv", "log", "json");

    public String extract(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        try {
            String text;
            if (PLAIN.contains(ext)) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else if (ext.equals("pdf")) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    if (doc.isEncrypted()) throw new IllegalArgumentException("This PDF is password protected.");
                    text = new PDFTextStripper().getText(doc);
                }
            } else if (ext.equals("docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    text = ex.getText();
                }
            } else {
                throw new IllegalArgumentException("Unsupported file type. Use txt, md, csv, pdf or docx.");
            }
            return normalise(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read. Is it a valid " + ext + " file?");
        }
    }

    public static String normalise(String text) {
        return text.replace("﻿", "").replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
