package hello.tradexserver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String message;

    public static MessageResponse of(String message) {
        return MessageResponse.builder().message(message).build();
    }
}
