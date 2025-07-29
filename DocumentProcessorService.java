package com.documentinsights.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.Loader;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DocumentProcessorService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    /**
     * Process different types of documents and extract text content
     * @param filePath Path to the uploaded file
     * @param mimeType MIME type of the file
     * @return Extracted text content
     * @throws IOException if file processing fails
     */
    public String processDocument(String filePath, String mimeType) throws IOException {
        Path path = Path.of(filePath);
        String extension = getFileExtension(path.getFileName().toString()).toLowerCase();
        
        log.info("Processing document: {} with extension: {}", filePath, extension);
        
        try {
            String content;
            switch (extension) {
                case ".pdf":
                    content = processPDF(path);
                    break;
                case ".docx":
                    content = processDocx(path);
                    break;
                case ".doc":
                    content = processDoc(path);
                    break;
                case ".txt":
                    content = processText(path);
                    break;
                default:
                    throw new IOException("Unsupported file type: " + extension);
            }
            
            if (content == null || content.trim().isEmpty()) {
                throw new IOException("No text content found in document");
            }
            
            return cleanText(content);
        } catch (Exception e) {
            log.error("Error processing document: {}", filePath, e);
            throw new IOException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Process PDF files using PDFBox
     */
    private String processPDF(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(filePath.toString()))) {
            if (document.isEncrypted()) {
                throw new IOException("Encrypted PDF files are not supported");
            }
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            
            if (text.trim().isEmpty()) {
                throw new IOException("No text content found in PDF");
            }
            
            return text;
        }
    }

    /**
     * Process DOCX files using Apache POI
     */
    private String processDocx(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            
            String text = extractor.getText();
            
            if (text.trim().isEmpty()) {
                throw new IOException("No text content found in DOCX document");
            }
            
            return text;
        }
    }

    /**
     * Process DOC files using Apache POI
     */
    private String processDoc(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            
            String text = extractor.getText();
            
            if (text.trim().isEmpty()) {
                throw new IOException("No text content found in DOC document");
            }
            
            return text;
        }
    }

    /**
     * Process text files
     */
    private String processText(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        if (content.trim().isEmpty()) {
            throw new IOException("Text file is empty");
        }
        
        return content;
    }

    /**
     * Clean and normalize text content
     */
    private String cleanText(String text) {
        if (text == null) return "";
        
        return text
                // Remove control characters except newlines and tabs
                .replaceAll(CONTROL_CHARS.pattern(), "")
                // Normalize line breaks
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                // Remove excessive whitespace
                .replaceAll(EXCESSIVE_WHITESPACE.pattern(), " ")
                // Remove excessive newlines
                .replaceAll(EXCESSIVE_NEWLINES.pattern(), "\n\n")
                // Trim whitespace
                .trim();
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : filename.substring(lastDotIndex);
    }

    /**
     * Validate file before processing
     */
    public void validateFile(Path filePath, long maxSizeBytes) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist");
        }
        
        long fileSize = Files.size(filePath);
        
        if (fileSize > maxSizeBytes) {
            throw new IOException(String.format("File too large: %.2f MB exceeds limit", 
                fileSize / 1024.0 / 1024.0));
        }
        
        if (fileSize == 0) {
            throw new IOException("File is empty");
        }
        
        String extension = getFileExtension(filePath.getFileName().toString()).toLowerCase();
        if (!isSupportedFileType(extension)) {
            throw new IOException("Unsupported file type: " + extension);
        }
    }

    /**
     * Check if file type is supported
     */
    private boolean isSupportedFileType(String extension) {
        return extension.equals(".pdf") || 
               extension.equals(".doc") || 
               extension.equals(".docx") || 
               extension.equals(".txt");
    }

    /**
     * Get document metadata
     */
    public DocumentMetadata getDocumentMetadata(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist");
        }
        
        long fileSize = Files.size(filePath);
        String extension = getFileExtension(filePath.getFileName().toString()).toLowerCase();
        
        return DocumentMetadata.builder()
                .size(fileSize)
                .extension(extension)
                .type(getFileTypeDescription(extension))
                .build();
    }

    /**
     * Get human-readable file type description
     */
    private String getFileTypeDescription(String extension) {
        return switch (extension) {
            case ".pdf" -> "PDF Document";
            case ".docx" -> "Microsoft Word Document (DOCX)";
            case ".doc" -> "Microsoft Word Document (DOC)";
            case ".txt" -> "Text Document";
            default -> "Unknown";
        };
    }

    /**
     * Document metadata holder
     */
    @lombok.Data
    @lombok.Builder
    public static class DocumentMetadata {
        private long size;
        private String extension;
        private String type;
    }
} 