package vn.edu.uit.edulens.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodayScheduleItem {

    private UUID classId;

    private UUID scheduleId;

    private String classCode;

    private String className;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    private String room;

    private String scheduleType;
}