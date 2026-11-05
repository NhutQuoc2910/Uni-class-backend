package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleExceptionRequest {

    @NotNull
    private ScheduleExceptionType exceptionType;

    private UUID classScheduleId;

    private LocalDate originalDate;

    private LocalDate occurrenceDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Size(max = 100)
    private String room;

    private String reason;

    private UUID relatedExceptionId;
}