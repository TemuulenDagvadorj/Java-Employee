package hrm.common;

import java.io.Serializable;
import java.time.LocalDate;


public class LeaveApplication implements Serializable {
    private static final long serialVersionUID = 1L;

    
    public static final String[] TYPES = {"Annual", "Sick", "Emergency", "Unpaid"};

    private String leaveId;        
    private String employeeId;     
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status = LeaveStatus.PENDING;
    private LocalDate appliedOn;   

    public LeaveApplication() { }

    public LeaveApplication(String leaveType, LocalDate startDate, LocalDate endDate, String reason) {
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    
    public int getDays() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    public LeaveApplication copy() {
        LeaveApplication c = new LeaveApplication(leaveType, startDate, endDate, reason);
        c.leaveId = leaveId;
        c.employeeId = employeeId;
        c.status = status;
        c.appliedOn = appliedOn;
        return c;
    }

    public String getLeaveId() { return leaveId; }
    public void setLeaveId(String leaveId) { this.leaveId = leaveId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }

    public LocalDate getAppliedOn() { return appliedOn; }
    public void setAppliedOn(LocalDate appliedOn) { this.appliedOn = appliedOn; }
}
