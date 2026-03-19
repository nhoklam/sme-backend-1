package sme.backend.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.config.AppProperties;
import sme.backend.entity.User;
import sme.backend.repository.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AiService — RAG (Retrieval-Augmented Generation) Co-pilot
 *
 * Architecture:
 * 1. SYS-03: Upload file → Tika extract text → TokenTextSplitter chunking
 * → Gemini embedding → lưu vào pgvector
 * 2. Chat:   User message → pgvector similarity search → top-K chunks
 * → System prompt + context + message → Gemini → response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final AppProperties appProperties;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────
    // SYS-03: UPLOAD & INDEX DOCUMENT
    // ─────────────────────────────────────────────────────────
    @Transactional
    public int indexDocument(Resource fileResource, String documentTitle, UUID uploadedBy) {
        log.info("Indexing document: {}", documentTitle);

        // 1. Extract text với Apache Tika (hỗ trợ PDF, DOCX, PPTX...)
        TikaDocumentReader reader = new TikaDocumentReader(fileResource);
        List<Document> rawDocs = reader.get();

        // 2. Thêm metadata để RAG có thể lọc
        rawDocs.forEach(doc -> {
            doc.getMetadata().put("source", documentTitle);
            doc.getMetadata().put("uploadedBy", uploadedBy.toString());
            doc.getMetadata().put("indexedAt", Instant.now().toString());
        });

        // 3. Chunking: chia nhỏ văn bản
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(appProperties.getAi().getChunkSize())
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        List<Document> chunks = splitter.apply(rawDocs);

        // 4. Vector hóa + lưu vào pgvector
        vectorStore.add(chunks);

        log.info("Document indexed: {} chunks from '{}'", chunks.size(), documentTitle);
        return chunks.size();
    }

    // ─────────────────────────────────────────────────────────
    // AI CHAT — RAG Co-pilot (SYS-03 + MODULE AI)
    // ─────────────────────────────────────────────────────────
    public String chat(String userMessage, UUID userId, String conversationHistory) {
        // Lấy thông tin user để personalise
        String userName = userRepository.findById(userId)
                .map(User::getFullName).orElse("người dùng");

        // 1. Tìm kiếm Vector thủ công (Manual RAG)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userMessage)
                        .topK(appProperties.getAi().getTopKResults())
                        .similarityThreshold(0.65)
                        .build()
        );

        // 2. Nối nội dung các chunk tìm được thành chuỗi Context
        // ĐÃ SỬA LỖI Ở ĐÂY: Sử dụng getFormattedContent() thay vì getContent()
        String context = similarDocuments.stream()
                .map(Document::getFormattedContent) 
                .collect(Collectors.joining("\n---\n"));

        // 3. Đưa Context vào System Prompt
        String systemPrompt = buildSystemPrompt(userName, conversationHistory, context);

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage(), e);
            return "Xin lỗi, tôi gặp sự cố khi xử lý yêu cầu của bạn. Vui lòng thử lại.";
        }
    }

    // ─────────────────────────────────────────────────────────
    // VECTOR SEARCH (dùng cho sản phẩm tương tự)
    // ─────────────────────────────────────────────────────────
    public List<Document> searchSimilar(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.6)
                        .build()
        );
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────
    private String buildSystemPrompt(String userName, String conversationHistory, String context) {
        return """
                Bạn là AI Co-pilot của hệ thống SME ERP & POS, trợ lý thông minh cho doanh nghiệp vừa và nhỏ.
                
                Vai trò của bạn:
                - Trả lời câu hỏi về nghiệp vụ: bán hàng, kho, tài chính, đơn hàng
                - Phân tích dữ liệu và đưa ra gợi ý kinh doanh
                - Hỗ trợ tra cứu chính sách, quy trình nội bộ từ tài liệu đã upload
                - Giao tiếp bằng tiếng Việt, thân thiện và chuyên nghiệp
                
                Tên người dùng: %s
                
                THÔNG TIN NGỮ CẢNH TỪ TÀI LIỆU (RAG):
                %s
                
                LỊCH SỬ HỘI THOẠI:
                %s
                
                Lưu ý quan trọng:
                - Ưu tiên sử dụng "THÔNG TIN NGỮ CẢNH" để trả lời nếu câu hỏi liên quan đến tài liệu.
                - Nếu không tìm thấy thông tin trong ngữ cảnh, hãy nói rõ và tuyệt đối không bịa đặt (hallucinate).
                - Với câu hỏi về số liệu thực tế (doanh thu, tồn kho), hãy hướng dẫn người dùng xem báo cáo.
                - Luôn trả lời ngắn gọn, có cấu trúc rõ ràng.
                """.formatted(
                userName,
                context.isBlank() ? "Không có tài liệu liên quan nào được tìm thấy." : context,
                conversationHistory != null ? conversationHistory : "Đây là đầu cuộc hội thoại");
    }
}