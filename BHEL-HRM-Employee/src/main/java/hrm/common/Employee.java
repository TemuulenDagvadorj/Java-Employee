package hrm.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class Employee implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String employeeId;
    private String firstName;
    private String lastName;
    private String icPassport;
    private Role role;
    private int leaveBalance;
    private List<FamilyMember> family = new ArrayList<>();

    public Employee(String employeeId, String firstName, String lastName,
                    String icPassport, Role role, int leaveBalance) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.icPassport = icPassport;
        this.role = role;
        this.leaveBalance = leaveBalance;
    }

    /** Defensive copy (used by the server before returning data to a client). */
    public Employee copy() {
        Employee c = new Employee(employeeId, firstName, lastName, icPassport, role, leaveBalance);
        c.family = new ArrayList<>(family);
        return c;
    }

    public String getEmployeeId() { return employeeId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getIcPassport() { return icPassport; }
    public void setIcPassport(String icPassport) { this.icPassport = icPassport; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public int getLeaveBalance() { return leaveBalance; }
    public void setLeaveBalance(int leaveBalance) { this.leaveBalance = leaveBalance; }

    public List<FamilyMember> getFamily() { return new ArrayList<>(family); }
    public void setFamily(List<FamilyMember> family) { this.family = new ArrayList<>(family); }

    @Override
    public String toString() {
        return employeeId + " - " + firstName + " " + lastName;
    }
}
