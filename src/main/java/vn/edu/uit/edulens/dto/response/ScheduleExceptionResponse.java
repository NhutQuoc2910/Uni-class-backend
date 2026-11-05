package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionStatus;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleExceptionResponse {

    private UUID id;
    private UUID classId;
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