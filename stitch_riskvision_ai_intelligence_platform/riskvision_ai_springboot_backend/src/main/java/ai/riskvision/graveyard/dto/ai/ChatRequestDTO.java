package ai.riskvision.graveyard.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequestDTO {
    private String message;
    private String prompt;
    @Builder.Default
    private List<ChatMessageDTO> history = new ArrayList<>();

    public String getEffectiveMessage() {
        if (message != null && !message.isBlank()) return message.trim();
        if (prompt != null && !prompt.isBlank()) return prompt.trim();
        return "";
    }
}
