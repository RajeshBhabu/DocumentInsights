# Low Level Design (LLD) - Document Insights Spring Boot Backend

## 1. Package Structure

```
com.documentinsights/
├── DocumentInsightsApplication.java          # Main Spring Boot application
├── config/
│   ├── CacheConfig.java                     # Caffeine cache configuration
│   └── SecurityConfig.java                 # Spring Security configuration
├── controller/
│   └── DocumentController.java             # REST API controller
├── dto/                                     # Data Transfer Objects
│   ├── ConfluenceRequest.java
│   ├── DocumentDto.java
│   ├── InsightsRequest.java
│   ├── InsightsResponse.java
│   └── UploadResponse.java
├── exception/
│   └── GlobalExceptionHandler.java         # Global exception handling
├── model/
│   └── Document.java                       # JPA entity
├── repository/
│   └── DocumentRepository.java             # Data access layer
└── service/                                # Business logic layer
    ├── AIService.java
    ├── ConfluenceService.java
    ├── DocumentProcessorService.java
    └── DocumentService.java
```

## 2. Entity Design

### 2.1 Document Entity

```java
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String originalName;
    
    @Column(nullable = false)
    private String type; // 'upload' or 'confluence'
    
    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;
    
    private String filePath;
    private String url; // For confluence documents
    
    @Column(nullable = false)
    private LocalDateTime uploadedAt;
    
    private Long fileSize;
    private String fileExtension;
    private String mimeType;
}
```

### 2.2 Database Schema

```sql
CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content CLOB,
    file_path VARCHAR(500),
    url VARCHAR(500),
    uploaded_at TIMESTAMP NOT NULL,
    file_size BIGINT,
    file_extension VARCHAR(10),
    mime_type VARCHAR(100)
);

-- Indexes for performance
CREATE INDEX idx_documents_type ON documents(type);
CREATE INDEX idx_documents_uploaded_at ON documents(uploaded_at);
CREATE INDEX idx_documents_original_name ON documents(original_name);
```

## 3. Repository Layer

### 3.1 DocumentRepository Interface

```java
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    // Content search methods
    List<Document> findByContentContaining(String keyword);
    
    // Type-based queries
    List<Document> findByType(String type);
    long countByType(String type);
    
    // Statistical queries
    @Query("SELECT SUM(d.fileSize) FROM Document d WHERE d.fileSize IS NOT NULL")
    Long getTotalFileSize();
    
    // Date-based queries
    List<Document> findByUploadedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Advanced search
    @Query("SELECT d FROM Document d WHERE " +
           "LOWER(d.originalName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Document> searchByKeyword(@Param("keyword") String keyword);
}
```

## 4. Service Layer Implementation

### 4.1 DocumentService - Core Business Logic

```java
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
    
    // File upload processing
    public UploadResponse uploadDocument(MultipartFile file) throws IOException {
        validateFile(file);
        Path filePath = saveFileToStorage(file);
        String content = documentProcessor.processDocument(filePath.toString(), file.getContentType());
        Document document = createDocumentEntity(file, filePath, content);
        Document savedDocument = documentRepository.save(document);
        return buildUploadResponse(savedDocument);
    }
    
    // Confluence content processing
    public UploadResponse addConfluenceContent(ConfluenceRequest request) throws IOException {
        ConfluenceService.ConfluenceContent content = confluenceService.fetchConfluenceContent(
            request.getUrl(), request.getConfluenceToken(), request.getConfluenceEmail());
        Document document = createConfluenceDocument(content, request.getUrl());
        Document savedDocument = documentRepository.save(document);
        return buildUploadResponse(savedDocument);
    }
    
    // AI insights generation
    public InsightsResponse generateInsights(InsightsRequest request) throws IOException {
        List<Document> documents = getRelevantDocuments(request.getDocumentIds());
        String insights = aiService.generateInsights(request.getQuery(), documents);
        return buildInsightsResponse(request.getQuery(), insights, documents.size());
    }
    
    // Document management methods
    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments() { /* Implementation */ }
    
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(Long id) throws IOException { /* Implementation */ }
    
    public void deleteDocument(Long id) throws IOException { /* Implementation */ }
    
    @Transactional(readOnly = true)
    public List<DocumentDto> searchDocuments(String keyword) { /* Implementation */ }
    
    @Transactional(readOnly = true)
    public DocumentStats getDocumentStats() { /* Implementation */ }
}
```

### 4.2 AIService - AI Provider Integration

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {
    
    @Value("${ai.provider:demo}")
    private String aiProvider;
    
    @Value("${openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;
    
    @Cacheable(value = "insights", key = "#query + '_' + #documents.hashCode()")
    public String generateInsights(String query, List<Document> documents) throws IOException {
        switch (aiProvider.toLowerCase()) {
            case "openai":
                return generateOpenAIInsights(query, documents);
            case "azure":
                return generateAzureInsights(query, documents);
            case "google":
                return generateGoogleInsights(query, documents);
            case "anthropic":
                return generateAnthropicInsights(query, documents);
            case "ollama":
                return generateOllamaInsights(query, documents);
            default:
                return generateDemoInsights(query, documents);
        }
    }
    
    private String generateOpenAIInsights(String query, List<Document> documents) throws IOException {
        // OpenAI API integration implementation
        String combinedContent = documents.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));
        
        String prompt = buildPrompt(query, combinedContent);
        return callOpenAIAPI(prompt);
    }
    
    private String buildPrompt(String query, String content) {
        return String.format(
            "Based on the following documents, please provide insights for this query: %s\n\n" +
            "Documents:\n%s\n\n" +
            "Please provide a comprehensive analysis addressing the query.",
            query, content
        );
    }
}
```

### 4.3 DocumentProcessorService - Content Extraction

```java
@Service
@Slf4j
public class DocumentProcessorService {
    
    public String processDocument(String filePath, String mimeType) throws IOException {
        switch (mimeType) {
            case "application/pdf":
                return extractPdfContent(filePath);
            case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return extractWordContent(filePath);
            case "text/plain":
                return extractTextContent(filePath);
            default:
                throw new IOException("Unsupported file type: " + mimeType);
        }
    }
    
    private String extractPdfContent(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            throw new IOException("Failed to extract PDF content: " + e.getMessage(), e);
        }
    }
    
    private String extractWordContent(String filePath) throws IOException {
        try {
            if (filePath.endsWith(".docx")) {
                XWPFDocument document = new XWPFDocument(new FileInputStream(filePath));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                return extractor.getText();
            } else {
                HWPFDocument document = new HWPFDocument(new FileInputStream(filePath));
                WordExtractor extractor = new WordExtractor(document);
                return extractor.getText();
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract Word content: " + e.getMessage(), e);
        }
    }
    
    public DocumentMetadata getDocumentMetadata(Path filePath) throws IOException {
        File file = filePath.toFile();
        return DocumentMetadata.builder()
            .fileSize(file.length())
            .lastModified(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()), 
                ZoneId.systemDefault()))
            .build();
    }
}
```

### 4.4 ConfluenceService - External API Integration

```java
@Service
@Slf4j
public class ConfluenceService {
    
    @Value("${confluence.timeout:30000}")
    private int timeout;
    
    public ConfluenceContent fetchConfluenceContent(String url, String token, String email) 
            throws IOException {
        try {
            RestTemplate restTemplate = createRestTemplate();
            HttpHeaders headers = createAuthHeaders(token, email);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                buildApiUrl(url), HttpMethod.GET, entity, String.class);
            
            return parseConfluenceResponse(response.getBody());
        } catch (Exception e) {
            throw new IOException("Failed to fetch Confluence content: " + e.getMessage(), e);
        }
    }
    
    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setRequestFactory(createRequestFactory());
        return template;
    }
    
    private HttpComponentsClientHttpRequestFactory createRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
    
    @Data
    @Builder
    public static class ConfluenceContent {
        private String title;
        private String content;
        private String space;
        private String version;
    }
}
```

## 5. Controller Layer Implementation

### 5.1 DocumentController - REST API Endpoints

```java
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class DocumentController {
    
    private final DocumentService documentService;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "timestamp", LocalDateTime.now(),
            "version", "1.0.0"
        ));
    }
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("document") MultipartFile file) {
        try {
            log.info("Received file upload request: {}", file.getOriginalFilename());
            UploadResponse response = documentService.uploadDocument(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error uploading document", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
    
    @PostMapping("/insights")
    public ResponseEntity<?> generateInsights(@Valid @RequestBody InsightsRequest request) {
        try {
            log.info("Received insights request: {}", request.getQuery());
            InsightsResponse response = documentService.generateInsights(request);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error generating insights", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
    
    // Additional endpoints with similar structure...
}
```

## 6. DTO Implementations

### 6.1 Request DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfluenceRequest {
    @NotBlank(message = "URL is required")
    @URL(message = "Invalid URL format")
    private String url;
    
    private String confluenceToken;
    private String confluenceEmail;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsRequest {
    @NotBlank(message = "Query is required")
    @Size(min = 3, max = 1000, message = "Query must be between 3 and 1000 characters")
    private String query;
    
    private List<Long> documentIds;
}
```

### 6.2 Response DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String message;
    private Long documentId;
    private String originalName;
    private String contentPreview;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsResponse {
    private String query;
    private String insights;
    private int documentsAnalyzed;
    private LocalDateTime timestamp;
    private String provider;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private Long id;
    private String originalName;
    private String type;
    private String contentPreview;
    private String url;
    private LocalDateTime uploadedAt;
    private Long fileSize;
    private String fileExtension;
    private String mimeType;
}
```

## 7. Configuration Implementation

### 7.1 Cache Configuration

```java
@Configuration
public class CacheConfig {
    
    @Value("${cache.insights.ttl:1800}")
    private long insightsTtl;
    
    @Value("${cache.insights.max-size:1000}")
    private long insightsMaxSize;
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Insights cache configuration
        cacheManager.registerCustomCache("insights", 
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(insightsTtl))
                .maximumSize(insightsMaxSize)
                .recordStats()
                .build());
        
        // Document summaries cache
        cacheManager.registerCustomCache("documentSummaries", 
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(500)
                .recordStats()
                .build());
        
        return cacheManager;
    }
}
```

### 7.2 Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .frameOptions().disable()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                )
            );
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

## 8. Exception Handling

### 8.1 Global Exception Handler

```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException e) {
        log.error("IO Exception occurred", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .error(e.getMessage())
                .type("IO_ERROR")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException e) {
        log.error("Validation Exception occurred", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .error(e.getMessage())
                .type("VALIDATION_ERROR")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(", "));
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .error(errorMessage)
                .type("VALIDATION_ERROR")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("Unexpected exception occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .error("Internal server error occurred")
                .type("INTERNAL_ERROR")
                .timestamp(LocalDateTime.now())
                .build());
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String type;
    private LocalDateTime timestamp;
}
```

## 9. Application Configuration

### 9.1 Main Application Class

```java
@SpringBootApplication
@EnableCaching
@EnableJpaRepositories
@EntityScan("com.documentinsights.model")
public class DocumentInsightsApplication {
    
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DocumentInsightsApplication.class);
        app.setDefaultProperties(getDefaultProperties());
        app.run(args);
    }
    
    private static Properties getDefaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        properties.setProperty("spring.jpa.show-sql", "false");
        properties.setProperty("logging.level.com.documentinsights", "INFO");
        return properties;
    }
    
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
            .setMatchingStrategy(MatchingStrategies.STRICT)
            .setFieldMatchingEnabled(true)
            .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE);
        return mapper;
    }
}
```

## 10. Testing Structure

### 10.1 Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    
    @Mock
    private DocumentRepository documentRepository;
    
    @Mock
    private DocumentProcessorService documentProcessor;
    
    @Mock
    private AIService aiService;
    
    @InjectMocks
    private DocumentService documentService;
    
    @Test
    void uploadDocument_ValidFile_ReturnsUploadResponse() throws IOException {
        // Given
        MultipartFile file = createMockFile();
        Document savedDocument = createMockDocument();
        
        when(documentProcessor.processDocument(anyString(), anyString()))
            .thenReturn("Extracted content");
        when(documentRepository.save(any(Document.class)))
            .thenReturn(savedDocument);
        
        // When
        UploadResponse response = documentService.uploadDocument(file);
        
        // Then
        assertThat(response.getDocumentId()).isEqualTo(savedDocument.getId());
        assertThat(response.getOriginalName()).isEqualTo(savedDocument.getOriginalName());
        verify(documentRepository).save(any(Document.class));
    }
}
```

### 10.2 Integration Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "ai.provider=demo"
})
class DocumentControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Test
    void uploadDocument_ValidFile_ReturnsSuccessResponse() {
        // Given
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("document", new FileSystemResource("test-file.txt"));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        // When
        ResponseEntity<UploadResponse> response = restTemplate.postForEntity(
            "/api/upload", 
            new HttpEntity<>(body, headers), 
            UploadResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDocumentId()).isNotNull();
        assertThat(documentRepository.count()).isEqualTo(1);
    }
}
```

## 11. Performance Optimization

### 11.1 Database Optimization

```java
// Optimized repository methods with custom queries
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    @Query("SELECT new com.documentinsights.dto.DocumentSummaryDto(" +
           "d.id, d.originalName, d.type, d.uploadedAt) " +
           "FROM Document d ORDER BY d.uploadedAt DESC")
    List<DocumentSummaryDto> findAllSummaries();
    
    @Query("SELECT d FROM Document d WHERE d.type = :type " +
           "ORDER BY d.uploadedAt DESC")
    Page<Document> findByTypeOrderByUploadedAtDesc(
        @Param("type") String type, Pageable pageable);
}
```

### 11.2 Memory Management

```java
@Service
public class DocumentProcessorService {
    
    // Streaming approach for large files
    public String processLargeDocument(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                
                // Prevent memory overflow
                if (content.length() > MAX_CONTENT_SIZE) {
                    content.setLength(MAX_CONTENT_SIZE);
                    content.append("... [Content truncated]");
                    break;
                }
            }
        }
        
        return content.toString();
    }
}
```

This Low Level Design provides comprehensive implementation details for all components of the Spring Boot backend, including class structures, method signatures, database schema, and configuration specifics. 