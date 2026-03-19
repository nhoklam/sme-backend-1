package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sme.backend.ai.AiService;
import sme.backend.dto.response.ApiResponse;
import sme.backend.security.UserPrincipal;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * POST /ai/chat — AI Co-pilot chat
     * Body: { "message": "...", "conversationHistory": "..." }
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String message = body.get("message");
        String history = body.get("conversationHistory");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<Map<String, String>>builder()
                            .success(false).message("message bắt buộc").build());
        }

        String reply = aiService.chat(message, principal.getId(), history);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("reply", reply)));
    }

    /**
     * POST /ai/documents — SYS-03: Upload tài liệu RAG
     * Hỗ trợ: PDF, DOCX, PPTX, TXT
     */
    @PostMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        String documentTitle = title != null ? title : file.getOriginalFilename();

        // Convert MultipartFile to Spring Resource
        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() { return file.getOriginalFilename(); }
                };

        int chunks = aiService.indexDocument(resource, documentTitle, principal.getId());

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "title",  documentTitle,
                "chunks", chunks,
                "status", "indexed"
        )));
    }

    /**
     * GET /ai/search — Tìm kiếm ngữ nghĩa trong tài liệu
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<?>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        var results = aiService.searchSimilar(query, topK);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
