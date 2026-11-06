package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassScheduleResponse {

    private UUID id;
    private UUID classId;
    private String classCode;
    private String className;

    private Integer dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;

    private String room;

    private LocalDate startDate;
    private LocalDate endDate;
}
