package lk.ac.sliit.tgms.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiNotFoundController {

    @RequestMapping("/api/{*path}")
    public ResponseEntity<ApiErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "NOT_FOUND", "The requested resource was not found."));
    }
}
