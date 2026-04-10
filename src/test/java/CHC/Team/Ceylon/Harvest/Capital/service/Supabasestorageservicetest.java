package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// AC-2: only image/jpeg, image/png, and application/pdf are accepted; all other MIME types are rejected
// AC-3: files larger than 5 MB are rejected before reaching Supabase
// AC-4: valid files are uploaded to Supabase Storage and a public URL is returned
// AC-6: a descriptive BadRequestException is thrown for invalid type or size
@ExtendWith(MockitoExtension.class)
class SupabaseStorageServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SupabaseStorageService supabaseStorageService;

    private static final String SUPABASE_URL = "https://fake.supabase.co";
    private static final String SERVICE_ROLE_KEY = "test-service-role-key";
    private static final String BUCKET_NAME = "evidence";

    @BeforeEach
    void setUp() {
        supabaseStorageService = new SupabaseStorageService(
                restTemplate, SUPABASE_URL, SERVICE_ROLE_KEY, BUCKET_NAME);
    }

    // ── AC-4: accepted types are successfully uploaded ────────────────────────

    @Test
    void upload_shouldSucceedAndReturnPublicUrlForJpeg() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "valid-jpg".getBytes());

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        String result = supabaseStorageService.upload(file);

        assertTrue(result.startsWith(SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/milestones/"),
                "Returned URL should point to the correct Supabase public path");
        assertTrue(result.endsWith(".jpg"), "URL should retain .jpg extension");
    }

    @Test
    void upload_shouldSucceedAndReturnPublicUrlForPng() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "snapshot.png", "image/png", "valid-png".getBytes());

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        String result = supabaseStorageService.upload(file);

        assertTrue(result.contains("/milestones/"));
        assertTrue(result.endsWith(".png"));
    }

    @Test
    void upload_shouldSucceedAndReturnPublicUrlForPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "valid-pdf".getBytes());

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        String result = supabaseStorageService.upload(file);

        assertTrue(result.contains("/milestones/"));
        assertTrue(result.endsWith(".pdf"));
    }

    // ── AC-2 / AC-6: unsupported MIME types are rejected ─────────────────────

    @Test
    void upload_shouldThrowBadRequestForGifContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "animation.gif", "image/gif", "fake-gif".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Unsupported file type. Only JPG, PNG, and PDF are accepted.", ex.getMessage());
    }

    @Test
    void upload_shouldThrowBadRequestForDocxContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "evidence.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Unsupported file type. Only JPG, PNG, and PDF are accepted.", ex.getMessage());
    }

    @Test
    void upload_shouldThrowBadRequestForMp4ContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "field-video.mp4", "video/mp4", "fake-mp4".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Unsupported file type. Only JPG, PNG, and PDF are accepted.", ex.getMessage());
    }

    @Test
    void upload_shouldThrowBadRequestWhenContentTypeIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "unknown-file", null, "bytes".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Unsupported file type. Only JPG, PNG, and PDF are accepted.", ex.getMessage());
    }

    // ── AC-3 / AC-6: files over 5 MB are rejected ────────────────────────────

    @Test
    void upload_shouldThrowBadRequestWhenFileSizeExceeds5Mb() {
        byte[] oversizedContent = new byte[5 * 1024 * 1024 + 1]; // 5 MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "files", "large-photo.jpg", "image/jpeg", oversizedContent);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("File size exceeds 5 MB limit. Please upload smaller file.", ex.getMessage());
    }

    @Test
    void upload_shouldAcceptFilesAtExactly5MbBoundary() {
        byte[] exactLimit = new byte[5 * 1024 * 1024]; // exactly 5 MB
        MockMultipartFile file = new MockMultipartFile(
                "files", "boundary-photo.jpg", "image/jpeg", exactLimit);

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        // Should not throw — 5 MB is within the allowed limit
        String result = supabaseStorageService.upload(file);
        assertTrue(result.contains("/milestones/"));
    }

    // ── AC-4: empty file is rejected ──────────────────────────────────────────

    @Test
    void upload_shouldThrowBadRequestForEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "empty.jpg", "image/jpeg", new byte[0]);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Uploaded file must not be empty.", ex.getMessage());
    }

    // ── AC-6: Supabase errors produce descriptive messages ───────────────────

    @Test
    void upload_shouldThrowBadRequestOnSupabase4xxResponse() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "valid-jpg".getBytes());

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", null, null, null));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertTrue(ex.getMessage().startsWith("File upload rejected by Supabase:"));
    }

    @Test
    void upload_shouldThrowBadRequestOnSupabase5xxResponse() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "valid-jpg".getBytes());

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> supabaseStorageService.upload(file));

        assertEquals("Supabase server error. Try again later.", ex.getMessage());
    }
}