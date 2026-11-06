package vn.edu.uit.edulens.dto.response;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterScheduleResponse {
    @Builder.Default
    private List<ClassScheduleResponse> regularSchedules = new ArrayList<>();

    @Builder.Default
    private List<ScheduleExceptionResponse> scheduleExceptions = new ArrayList<>();
}
