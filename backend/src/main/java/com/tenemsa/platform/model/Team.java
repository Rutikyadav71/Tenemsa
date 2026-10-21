package com.tenemsa.platform.model;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name="teams",indexes=@Index(name="idx_teams_company",columnList="company_id"))
public class Team {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(nullable=false) private String name;
    private String description;
    @Column(name="project_name") private String projectName;
    @Column(name="created_date") private LocalDate createdDate=LocalDate.now();
    private LocalDate deadline;
    @Enumerated(EnumType.STRING) private TeamStatus status=TeamStatus.ACTIVE;
    @ElementCollection @CollectionTable(name="team_members",joinColumns=@JoinColumn(name="team_id")) @Column(name="emp_id")
    private List<Integer> memberIds=new ArrayList<>();
    @Column(name="team_lead_id") private Integer teamLeadId;
    public Long getId(){return id;} public void setId(Long i){id=i;}
    public Long getCompanyId(){return companyId;} public void setCompanyId(Long c){companyId=c;}
    public String getName(){return name;} public void setName(String n){name=n;}
    public String getDescription(){return description;} public void setDescription(String d){description=d;}
    public String getProjectName(){return projectName;} public void setProjectName(String p){projectName=p;}
    public LocalDate getCreatedDate(){return createdDate;} public void setCreatedDate(LocalDate d){createdDate=d;}
    public LocalDate getDeadline(){return deadline;} public void setDeadline(LocalDate d){deadline=d;}
    public TeamStatus getStatus(){return status;} public void setStatus(TeamStatus s){status=s;}
    public List<Integer> getMemberIds(){return memberIds;} public void setMemberIds(List<Integer> m){memberIds=m;}
    public Integer getTeamLeadId(){return teamLeadId;} public void setTeamLeadId(Integer i){teamLeadId=i;}
}