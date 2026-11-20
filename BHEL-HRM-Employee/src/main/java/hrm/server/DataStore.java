package hrm.server;

import hrm.common.Employee;
import hrm.common.FamilyMember;
import hrm.common.LeaveApplication;
import hrm.common.LeaveStatus;
import hrm.common.Role;
import hrm.common.ServiceResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class DataStore {
    public static final int DEFAULT_LEAVE_BALANCE = 14;

    private static final DataStore INSTANCE = new DataStore();

    public static DataStore getInstance() {
        return INSTANCE;
    }

    private final Map<String, Employee> employees = new HashMap<>();
    private final Map<String, String[]> credentials = new HashMap<>(); // id -> {salt, hash}
    private final List<LeaveApplication> leaves = new ArrayList<>();
    private int leaveCounter = 0;

    private DataStore() {
        seedDemoData();
    }

    
    private void seedDemoData() {
        addEmployee(new Employee("E001", "Ali", "Ahmad", "900101-14-1234", Role.EMPLOYEE, DEFAULT_LEAVE_BALANCE), "1234");
        addEmployee(new Employee("E002", "Siti", "Aminah", "920202-10-5678", Role.EMPLOYEE, DEFAULT_LEAVE_BALANCE), "1234");
        addEmployee(new Employee("H001", "Hana", "Lim", "850505-08-1111", Role.HR, DEFAULT_LEAVE_BALANCE), "hr1234");
        addEmployee(new Employee("A001", "Admin", "User", "800808-01-2222", Role.ADMIN, DEFAULT_LEAVE_BALANCE), "admin1234");
    }

    

    
    public synchronized boolean addEmployee(Employee employee, String plainPassword) {
        if (employees.containsKey(employee.getEmployeeId())) return false;
        for (Employee e : employees.values()) {
            if (e.getIcPassport().equalsIgnoreCase(employee.getIcPassport())) return false;
        }
        String salt = PasswordUtil.newSalt();
        employees.put(employee.getEmployeeId(), employee.copy());
        credentials.put(employee.getEmployeeId(), new String[]{salt, PasswordUtil.hash(plainPassword, salt)});
        return true;
    }

    
    public synchronized boolean removeEmployee(String employeeId) {
        credentials.remove(employeeId);
        return employees.remove(employeeId) != null;
    }

    public synchronized boolean exists(String employeeId) {
        return employees.containsKey(employeeId);
    }

    
    public synchronized Employee getEmployeeCopy(String employeeId) {
        Employee e = employees.get(employeeId);
        return e == null ? null : e.copy();
    }

    
    public synchronized List<Employee> getAllEmployees() {
        List<Employee> result = new ArrayList<>();
        for (Employee e : employees.values()) result.add(e.copy());
        return result;
    }

   
    public Employee authenticate(String employeeId, String password) {
        String[] cred;
        synchronized (this) {                 
            cred = credentials.get(employeeId);
        }
        if (cred == null) return null;
        
        if (!PasswordUtil.verify(password, cred[0], cred[1])) return null;
        return getEmployeeCopy(employeeId);
    }

    // profile / family

    public synchronized ServiceResult updateProfile(String employeeId, String first, String last, String icPassport) {
        Employee e = employees.get(employeeId);
        if (e == null) return ServiceResult.fail("Employee not found.");
        for (Employee other : employees.values()) {
            if (other != e && other.getIcPassport().equalsIgnoreCase(icPassport)) {
                return ServiceResult.fail("That IC/Passport number is already registered to another employee.");
            }
        }
        e.setFirstName(first);
        e.setLastName(last);
        e.setIcPassport(icPassport);
        return ServiceResult.ok("Profile updated.");
    }

    public synchronized ServiceResult updateFamily(String employeeId, List<FamilyMember> family) {
        Employee e = employees.get(employeeId);
        if (e == null) return ServiceResult.fail("Employee not found.");
        e.setFamily(family);
        return ServiceResult.ok("Family details saved (" + family.size() + " member(s)).");
    }

    //  leave

    public synchronized int getLeaveBalance(String employeeId) {
        Employee e = employees.get(employeeId);
        return e == null ? -1 : e.getLeaveBalance();
    }

    
    public synchronized ServiceResult applyLeave(String employeeId, LeaveApplication request) {
        Employee e = employees.get(employeeId);
        if (e == null) return ServiceResult.fail("Employee not found.");

        int pendingDays = 0;
        for (LeaveApplication l : leaves) {
            if (!l.getEmployeeId().equals(employeeId) || l.getStatus() == LeaveStatus.REJECTED) continue;
            boolean overlap = !(request.getEndDate().isBefore(l.getStartDate())
                    || request.getStartDate().isAfter(l.getEndDate()));
            if (overlap) {
                return ServiceResult.fail("These dates overlap with your leave " + l.getLeaveId()
                        + " (" + l.getStatus() + ").");
            }
            if (l.getStatus() == LeaveStatus.PENDING) pendingDays += l.getDays();
        }

        int available = e.getLeaveBalance() - pendingDays;
        if (request.getDays() > available) {
            return ServiceResult.fail("Insufficient leave balance. You requested " + request.getDays()
                    + " day(s) but only " + available + " day(s) are available (balance "
                    + e.getLeaveBalance() + " minus " + pendingDays + " day(s) already pending).");
        }

        LeaveApplication saved = request.copy();
        saved.setLeaveId(String.format("L%04d", ++leaveCounter));
        saved.setEmployeeId(employeeId);
        saved.setStatus(LeaveStatus.PENDING);
        saved.setAppliedOn(LocalDate.now());
        leaves.add(saved);
        return ServiceResult.ok("Leave application " + saved.getLeaveId() + " submitted (PENDING approval).");
    }

    
    public synchronized List<LeaveApplication> getLeavesFor(String employeeId) {
        List<LeaveApplication> result = new ArrayList<>();
        for (LeaveApplication l : leaves) {
            if (l.getEmployeeId().equals(employeeId)) result.add(l.copy());
        }
        return result;
    }

   
    public synchronized List<LeaveApplication> getAllLeaves() {
        List<LeaveApplication> result = new ArrayList<>();
        for (LeaveApplication l : leaves) result.add(l.copy());
        return result;
    }

    
    public synchronized ServiceResult decideLeave(String leaveId, LeaveStatus decision) {
        if (decision == LeaveStatus.PENDING) return ServiceResult.fail("Decision must be APPROVED or REJECTED.");
        for (LeaveApplication l : leaves) {
            if (!l.getLeaveId().equals(leaveId)) continue;
            if (l.getStatus() != LeaveStatus.PENDING) return ServiceResult.fail("Leave " + leaveId + " is already " + l.getStatus() + ".");
            if (decision == LeaveStatus.APPROVED) {
                Employee e = employees.get(l.getEmployeeId());
                if (e == null) return ServiceResult.fail("Employee no longer exists.");
                if (l.getDays() > e.getLeaveBalance()) return ServiceResult.fail("Employee has insufficient leave balance.");
                e.setLeaveBalance(e.getLeaveBalance() - l.getDays());
            }
            l.setStatus(decision);
            return ServiceResult.ok("Leave " + leaveId + " " + decision + ".");
        }
        return ServiceResult.fail("Leave application not found.");
    }
}
