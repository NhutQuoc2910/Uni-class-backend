package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionStatus;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleExceptionResponse {

    private UUID id;
    private UUID classId;
    private String classCode;
    private String className;
    private UUID classScheduleId;

    private LocalDate originalDate;
    private LocalDate occurrenceDate;

    private ScheduleExceptionType exceptionType;
    private ScheduleExceptionStatus status;

    private LocalTime startTime;
    private LocalTime endTime;

    private String room;
    private String reason;

    private UUID relatedExceptionId;
}
