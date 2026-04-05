package CHC.Team.Ceylon.Harvest.Capital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequest {

    @NotBlank(message = "Message cannot be empty")  // AC-6
    @Size(max = 1000, message = "Message is too long")
    private String message;
}