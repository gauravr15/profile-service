package com.odin.profileservice.dto;
import java.util.List;
import com.odin.profileservice.enums.StatusReadinessReason;
import com.odin.profileservice.enums.StatusReadinessState;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatusReadinessResponse {
    private StatusReadinessState state;
    private List<StatusReadinessReason> reasons;
    private List<String> repairActions;
    private boolean retryable;
    @Builder.Default private int readinessVersion = 1;
}
