package ai.riskvision.graveyard.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageDTO {
    private String role;   // "user", "assistant", or "system"
    private String sender; // "user" or "assistant"
    private String text;   // message text content
    private String content;// alternative message content field

    public String getEffectiveContent() {
        if (text != null && !text.isBlank()) return text;
        if (content != null && !content.isBlank()) return content;
        return "";
    }

    public String getEffectiveRole() {
        if (role != null && !role.isBlank()) return role.toLowerCase();
        if (sender != null) {
            if ("user".equalsIgnoreCase(sender)) return "user";
            if ("assistant".equalsIgnoreCase(sender)) return "assistant";
        }
        return "user";
    }
}
