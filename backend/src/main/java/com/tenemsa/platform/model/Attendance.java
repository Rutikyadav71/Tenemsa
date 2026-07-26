package com.tenemsa.platform.model;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
@Entity
@Table(name="attendance",uniqueConstraints=@UniqueConstraint(columnNames={"emp_id","date"}))
public class Attendance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="emp_id",nullable=false) private int empId;
    @Column(nullable=false) private LocalDate date;
    @Column(name="check_in") private LocalTime checkIn;
    @Column(name="check_out") private LocalTime checkOut;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AttendanceStatus status=AttendanceStatus.ABSENT;
    @Column(name="work_hours") private Double workHours;
    private String notes;
    @Column(name="marked_by") private String markedBy;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public int getEmpId(){return empId;} public void setEmpId(int e){empId=e;}
    public LocalDate getDate(){return date;} public void setDate(LocalDate d){date=d;}
    public LocalTime getCheckIn(){return checkIn;} public void setCheckIn(LocalTime t){checkIn=t;}
    public LocalTime getCheckOut(){return checkOut;} public void setCheckOut(LocalTime t){checkOut=t;}
    public AttendanceStatus getStatus(){return status;} public void setStatus(AttendanceStatus s){status=s;}
    public Double getWorkHours(){return workHours;} public void setWorkHours(Double h){workHours=h;}
    public String getNotes(){return notes;} public void setNotes(String n){notes=n;}
    public String getMarkedBy(){return markedBy;} public void setMarkedBy(String m){markedBy=m;}
}