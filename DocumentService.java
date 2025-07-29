package com.documentinsights.service;

import com.documentinsights.dto.*;
import com.documentinsights.model.Document;
import com.documentinsights.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessorService documentProcessor;
    private final ConfluenceService confluenceService;
    private final AIService aiService;

    @Value("${file.upload.directory}")
    private String uploadDirectory;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    /**
     * Upload and process a document
     */
    public UploadResponse uploadDocument(MultipartFile file) throws IOException {
        log.info("Uploading document: {}", file.getOriginalFilename());

        // Validate file
        validateFile(file);

        // Create upload directory if it doesn't exist
        Path uploadDir = Paths.get(uploadDirectory);
        Files.createDirectories(uploadDir);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadDir.resolve(uniqueFilename);

        try {
            // Save file to disk
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Validate file after saving
            documentProcessor.validateFile(filePath, maxFileSize);

            // Process document to extract text content
            String content = documentProcessor.processDocument(filePath.toString(), file.getContentType());

            // Get document metadata
            DocumentProcessorService.DocumentMetadata metadata = 
                documentProcessor.getDocumentMetadata(filePath);

            // Create document entity
            Document document = Document.builder()
                    .originalName(originalFilename)
                    .type("upload")
                    .content(content)
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .fileExtension(extension)
                    .mimeType(file.getContentType())
                    .build();

            // Save to database
            Document savedDocument = documentRepository.save(document);

            log.info("Document uploaded and processed successfully: {}", savedDocument.getId());

            return UploadResponse.builder()
                    .message("Document uploaded and processed successfully")
                    .documentId(savedDocument.getId())
                    .originalName(savedDocument.getOriginalName())
                    .contentPreview(savedDocument.getContentPreview(500))
                    .build();

        } catch (Exception e) {
            // Clean up file if processing failed
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException cleanupError) {
                log.warn("Failed to clean up file after processing error: {}", filePath, cleanupError);
            }
            throw e;
        }
    }

    /**
     * Add Confluence content
     */
    public UploadResponse addConfluenceContent(ConfluenceRequest request) throws IOException {
        log.info("Adding Confluence content from URL: {}", request.getUrl());

        try {
            // Fetch content from Confluence
            ConfluenceService.ConfluenceContent confluenceContent = 
                confluenceService.fetchConfluenceContent(
                    request.getUrl(), 
                    request.getConfluenceToken(), 
                    request.getConfluenceEmail()
                );

            // Create document entity
            Document document = Document.builder()
                    .originalName(confluenceContent.getTitle())
                    .type("confluence")
                    .content(confluenceContent.getContent())
                    .url(request.getUrl())
                    .build();

            // Save to database
            Document savedDocument = documentRepository.save(document);

            log.info("Confluence content added successfully: {}", savedDocument.getId());

            return UploadResponse.builder()
                    .message("Confluence content fetched and processed successfully")
                    .documentId(savedDocument.getId())
                    .originalName(savedDocument.getOriginalName())
                    .contentPreview(savedDocument.getContentPreview(500))
                    .build();

        } catch (Exception e) {
            log.error("Error adding Confluence content from URL: {}", request.getUrl(), e);
            throw new IOException("Failed to fetch Confluence content: " + e.getMessage(), e);
        }
    }

    /**
     * Generate insights from documents
     */
    public InsightsResponse generateInsights(InsightsRequest request) throws IOException {
        log.info("Generating insights for query: {}", request.getQuery());

        try {
            // Get relevant documents
            List<Document> documents;
            if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
                documents = documentRepository.findAllById(request.getDocumentIds());
                if (documents.size() != request.getDocumentIds().size()) {
                    throw new IOException("Some requested documents were not found");
                }
            } else {
                documents = documentRepository.findAll();
            }

            if (documents.isEmpty()) {
                throw new IOException("No documents available for analysis");
            }

            // Generate insights using AI
            String insights = aiService.generateInsights(request.getQuery(), documents);

            return InsightsResponse.builder()
                    .query(request.getQuery())
                    .insights(insights)
                    .documentsAnalyzed(documents.size())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error generating insights for query: {}", request.getQuery(), e);
            throw new IOException("Failed to generate insights: " + e.getMessage(), e);
        }
    }

    /**
     * Get all documents
     */
    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments() {
        try {
            return documentRepository.findAll().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If table doesn't exist yet (first time usage), return empty list
            log.warn("Database table may not exist yet, returning empty list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get document by ID
     */
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(Long id) throws IOException {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IOException("Document not found with ID: " + id));
        return convertToDto(document);
    }

    /**
     * Delete document
     */
    public void deleteDocument(Long id) throws IOException {
        log.info("Deleting document: {}", id);

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IOException("Document not found with ID: " + id));

        try {
            // Delete file if it's an uploaded document
            if (document.getFilePath() != null && "upload".equals(document.getType())) {
                Path filePath = Paths.get(document.getFilePath());
                Files.deleteIfExists(filePath);
                log.info("Deleted file: {}", filePath);
            }

            // Delete from database
            documentRepository.delete(document);

            log.info("Document deleted successfully: {}", id);

        } catch (Exception e) {
            log.error("Error deleting document: {}", id, e);
            throw new IOException("Failed to delete document: " + e.getMessage(), e);
        }
    }

    /**
     * Search documents by content
     */
    @Transactional(readOnly = true)
    public List<DocumentDto> searchDocuments(String keyword) {
        return documentRepository.findByContentContaining(keyword).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get documents by type
     */
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsByType(String type) {
        return documentRepository.findByType(type).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get document statistics
     */
    @Transactional(readOnly = true)
    public DocumentStats getDocumentStats() {
        long totalDocuments = documentRepository.count();
        long uploadedDocuments = documentRepository.countByType("upload");
        long confluenceDocuments = documentRepository.countByType("confluence");
        Long totalFileSize = documentRepository.getTotalFileSize();

        return DocumentStats.builder()
                .totalDocuments(totalDocuments)
                .uploadedDocuments(uploadedDocuments)
                .confluenceDocuments(confluenceDocuments)
                .totalFileSize(totalFileSize != null ? totalFileSize : 0L)
                .build();
    }

    /**
     * Convert Document entity to DTO
     */
    private DocumentDto convertToDto(Document document) {
        return DocumentDto.builder()
                .id(document.getId())
                .originalName(document.getOriginalName())
                .type(document.getType())
                .contentPreview(document.getContentPreview())
                .url(document.getUrl())
                .uploadedAt(document.getUploadedAt())
                .fileSize(document.getFileSize())
                .fileExtension(document.getFileExtension())
                .mimeType(document.getMimeType())
                .build();
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("No file uploaded");
        }

        if (file.getSize() > maxFileSize) {
            throw new IOException(String.format("File too large: %.2f MB exceeds limit", 
                file.getSize() / 1024.0 / 1024.0));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IOException("Invalid filename");
        }

        String extension = getFileExtension(filename).toLowerCase();
        if (!isSupportedFileType(extension)) {
            throw new IOException("Unsupported file type: " + extension);
        }
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : filename.substring(lastDotIndex);
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
     * Document statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class DocumentStats {
        private long totalDocuments;
        private long uploadedDocuments;
        private long confluenceDocuments;
        private long totalFileSize;
    }
} 