package CHC.Team.Ceylon.Harvest.Capital.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageResponse {
    private String reply;
    private boolean success;
    private String error;
}