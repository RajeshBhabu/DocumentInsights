# High Level Design (HLD) - Document Insights Spring Boot Backend

## 1. System Overview

### 1.1 Purpose
The Document Insights application is an AI-powered document analysis system that allows users to upload documents, fetch content from Confluence, and generate intelligent insights using various AI providers. The Spring Boot backend serves as the core processing engine that handles document management, content extraction, and AI integration.

### 1.2 Key Features
- **Multi-format Document Processing**: Support for PDF, DOC, DOCX, and TXT files
- **Confluence Integration**: Fetch and process content from Confluence pages
- **AI-Powered Analysis**: Generate insights using multiple AI providers (OpenAI, Azure, Google, Anthropic, Ollama)
- **Document Management**: Full CRUD operations with search capabilities
- **Caching Layer**: Intelligent caching for AI responses and document metadata
- **Real-time Processing**: Asynchronous document processing with progress feedback

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   React Client  │───→│  Spring Boot API │───→│   AI Services   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   H2 Database   │
                       └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │  File Storage   │
                       └─────────────────┘
```

### 2.2 Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  Controllers (REST API Endpoints, Exception Handlers)      │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                    Business Logic Layer                     │
│     Services (Document, AI, Confluence, Processor)         │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                    Data Access Layer                        │
│         Repositories (JPA/Hibernate)                       │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                    Data Storage Layer                       │
│           H2 Database + File System                        │
└─────────────────────────────────────────────────────────────┘
```

## 3. Core Components

### 3.1 API Layer
- **DocumentController**: Main REST controller handling all document-related operations
- **GlobalExceptionHandler**: Centralized exception handling with proper HTTP status codes
- **SecurityConfig**: CORS and security configuration for API access
- **Health Check**: System health monitoring endpoint

### 3.2 Service Layer
- **DocumentService**: Core business logic for document management
- **AIService**: AI integration and insight generation
- **ConfluenceService**: Confluence API integration
- **DocumentProcessorService**: Document parsing and content extraction

### 3.3 Data Layer
- **DocumentRepository**: JPA repository for document persistence
- **Document Entity**: Core data model representing documents
- **DTOs**: Data transfer objects for API communication

### 3.4 Configuration Layer
- **CacheConfig**: Caffeine cache configuration for performance optimization
- **SecurityConfig**: Spring Security configuration
- **Application Properties**: Environment-specific configurations

## 4. Data Flow

### 4.1 Document Upload Flow
```
1. Client uploads file → DocumentController.uploadDocument()
2. File validation → DocumentService.validateFile()
3. File storage → Save to file system
4. Content extraction → DocumentProcessorService.processDocument()
5. Metadata extraction → DocumentProcessorService.getDocumentMetadata()
6. Database persistence → DocumentRepository.save()
7. Response generation → UploadResponse DTO
```

### 4.2 Confluence Content Flow
```
1. Client provides URL → DocumentController.addConfluenceContent()
2. Content fetching → ConfluenceService.fetchConfluenceContent()
3. Content processing → Extract title and content
4. Database persistence → DocumentRepository.save()
5. Response generation → UploadResponse DTO
```

### 4.3 AI Insights Flow
```
1. Client query → DocumentController.generateInsights()
2. Document retrieval → DocumentRepository.findAllById()
3. AI processing → AIService.generateInsights()
4. Cache check/update → Caffeine cache
5. Response generation → InsightsResponse DTO
```

## 5. API Design

### 5.1 RESTful Endpoints
| Method | Endpoint | Purpose | Request | Response |
|--------|----------|---------|---------|----------|
| GET | `/api/health` | Health check | None | Status object |
| POST | `/api/upload` | Upload document | MultipartFile | UploadResponse |
| POST | `/api/confluence` | Add Confluence content | ConfluenceRequest | UploadResponse |
| POST | `/api/insights` | Generate AI insights | InsightsRequest | InsightsResponse |
| GET | `/api/documents` | Get all documents | None | List<DocumentDto> |
| GET | `/api/documents/{id}` | Get document by ID | Path variable | DocumentDto |
| DELETE | `/api/documents/{id}` | Delete document | Path variable | Success message |
| GET | `/api/documents/search` | Search documents | Query param | List<DocumentDto> |
| GET | `/api/documents/type/{type}` | Get by type | Path variable | List<DocumentDto> |
| GET | `/api/documents/stats` | Get statistics | None | DocumentStats |

### 5.2 Request/Response Models
- **UploadResponse**: File upload confirmation with preview
- **ConfluenceRequest**: Confluence URL and authentication
- **InsightsRequest**: Query and document IDs for analysis
- **InsightsResponse**: AI-generated insights with metadata
- **DocumentDto**: Document representation for API responses

## 6. Security Architecture

### 6.1 Security Configuration
- **CORS Policy**: Configurable cross-origin resource sharing
- **Authentication**: Currently permit-all for development
- **CSRF Protection**: Disabled for stateless API
- **Frame Options**: Disabled for H2 console access

### 6.2 Input Validation
- **File Type Validation**: Only PDF, DOC, DOCX, TXT allowed
- **File Size Limits**: Configurable maximum file size (100MB)
- **Content Validation**: XSS protection through proper encoding
- **Request Validation**: Jakarta validation annotations

## 7. Caching Strategy

### 7.1 Cache Configuration
- **Insights Cache**: 30-minute TTL, 1000 entry limit
- **Document Summaries**: 24-hour TTL, 500 entry limit
- **Key Topics**: 12-hour TTL, 200 entry limit

### 7.2 Cache Implementation
- **Provider**: Caffeine cache (high-performance Java caching)
- **Eviction Policy**: Time-based and size-based eviction
- **Cache Keys**: Query + document hash for insights

## 8. AI Integration Architecture

### 8.1 Multi-Provider Support
- **OpenAI**: GPT models with configurable parameters
- **Azure OpenAI**: Enterprise OpenAI integration
- **Google Gemini**: Google's AI platform
- **Anthropic Claude**: Claude AI models
- **Ollama**: Local AI model hosting
- **Demo Mode**: Fallback for testing without API keys

### 8.2 AI Configuration
- **Provider Selection**: Environment-based switching
- **Model Parameters**: Configurable temperature, tokens, etc.
- **Fallback Strategy**: Demo responses when APIs unavailable
- **Rate Limiting**: Built-in provider rate limit handling

## 9. Storage Architecture

### 9.1 Database Design
- **H2 In-Memory**: Development database
- **JPA/Hibernate**: ORM for data persistence
- **Auto-DDL**: Schema generation from entities
- **Transaction Management**: Declarative transactions

### 9.2 File Storage
- **Local File System**: Temporary storage for uploads
- **Unique Naming**: UUID-based file naming
- **Directory Structure**: Configurable upload directory
- **Cleanup Strategy**: Orphan file cleanup on errors

## 10. Performance Considerations

### 10.1 Optimization Strategies
- **Caching**: Multi-level caching for AI responses
- **Lazy Loading**: JPA lazy loading for large content
- **Connection Pooling**: Database connection optimization
- **Async Processing**: Future support for background processing

### 10.2 Scalability Design
- **Stateless Design**: No server-side session state
- **Horizontal Scaling**: Stateless services support clustering
- **Resource Limits**: Configurable memory and file limits
- **Database Scaling**: Ready for external database migration

## 11. Monitoring and Logging

### 11.1 Logging Strategy
- **Structured Logging**: SLF4J with Logback
- **Log Levels**: Configurable per package
- **Error Tracking**: Comprehensive exception logging
- **Performance Metrics**: Response time logging

### 11.2 Health Monitoring
- **Health Endpoint**: System status verification
- **Database Health**: Connection and query health
- **AI Provider Health**: API availability checking
- **File System Health**: Storage availability monitoring

## 12. Deployment Architecture

### 12.1 Development Environment
- **Embedded Server**: Tomcat embedded in Spring Boot
- **H2 Console**: Web-based database management
- **Hot Reload**: Development-time code reloading
- **Profile-based Config**: Environment-specific settings

### 12.2 Production Considerations
- **External Database**: PostgreSQL/MySQL for production
- **File Storage**: Cloud storage integration (S3, Azure Blob)
- **Load Balancing**: Multiple instance deployment
- **Configuration Management**: External configuration server

## 13. Future Enhancements

### 13.1 Planned Features
- **User Authentication**: JWT-based authentication
- **Document Versioning**: Version control for documents
- **Batch Processing**: Bulk document upload and processing
- **Real-time Updates**: WebSocket for progress updates

### 13.2 Technical Improvements
- **Microservices**: Service decomposition for scalability
- **Event-Driven Architecture**: Async event processing
- **API Gateway**: Centralized API management
- **Container Deployment**: Docker/Kubernetes deployment 