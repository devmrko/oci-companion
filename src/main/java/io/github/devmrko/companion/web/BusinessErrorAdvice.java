package io.github.devmrko.companion.web;
import io.github.devmrko.companion.error.BusinessException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** Default for endpoints without their own HTML error view. */
@RestControllerAdvice
public class BusinessErrorAdvice {
 @ExceptionHandler(BusinessException.class)
 ResponseEntity<Map<String,String>> handle(BusinessException error){
  var status=switch(error.category()){case VALIDATION -> HttpStatus.BAD_REQUEST;case CONFLICT -> HttpStatus.CONFLICT;case NOT_FOUND -> HttpStatus.NOT_FOUND;case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;};
  return ResponseEntity.status(status).body(Map.of("code",error.code(),"message",error.getMessage()));
 }
}
