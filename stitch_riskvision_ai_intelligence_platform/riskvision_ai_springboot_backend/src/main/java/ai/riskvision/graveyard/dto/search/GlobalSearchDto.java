package ai.riskvision.graveyard.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSearchDto {

    private String query;
    private int totalCount;
    private List<SearchResultItem> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchResultItem {
        private String id;
        private String type; // REPOSITORY, SOURCE_FILE, FINDING, PREDICTION, PAGE
        private String title;
        private String subtitle;
        private String riskLevel; // CRITICAL, HIGH, MEDIUM, LOW, INFO
        private String url;
        private Map<String, Object> metadata;
    }
}
