package hrm.server;

import hrm.common.Employee;
import hrm.common.EmployeeService;
import hrm.common.FamilyMember;
import hrm.common.LeaveApplication;
import hrm.common.ServiceException;
import hrm.common.ServiceResult;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Employee services. Every method: (1) identify the caller from the token,
 * (2) validate the input on the SERVER (never trust the client), (3) hand the
 * work to DataStore. RMI runs each call on its own thread, so many employees
 * can use this object at the same time; shared state lives in DataStore.
 */
public class EmployeeServiceImpl extends UnicastRemoteObject implements EmployeeService {
    private static final long serialVersionUID = 1L;

    private static final Pattern NAME = Pattern.compile("^[\\p{L} .'-]{1,50}$");
    private static final Pattern IC_PASSPORT = Pattern.compile("^[A-Za-z0-9-]{6,20}$");
    private static final int MAX_FAMILY = 20;
    private static final int MAX_LEAVE_DAYS = 90;

    private final DataStore store = DataStore.getInstance();
    private final SessionManager sessions = SessionManager.getInstance();

    /** csf/ssf may be null (plain RMI) or SSL socket factories (secure RMI). */
    public EmployeeServiceImpl(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
    }

    @Override
    public Employee getProfile(String token) throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        return store.getEmployeeCopy(id);
    }

    @Override
    public ServiceResult updateProfile(String token, String firstName, String lastName, String icPassport)
            throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        String f = firstName == null ? "" : firstName.trim();
        String l = lastName == null ? "" : lastName.trim();
        String ic = icPassport == null ? "" : icPassport.trim();
        if (!NAME.matcher(f).matches()) return ServiceResult.fail("First name is required (letters only, max 50).");
        if (!NAME.matcher(l).matches()) return ServiceResult.fail("Last name is required (letters only, max 50).");
        if (!IC_PASSPORT.matcher(ic).matches()) return ServiceResult.fail("IC/Passport must be 6-20 letters, digits or '-'.");
        log(id, "updateProfile");
        return store.updateProfile(id, f, l, ic);
    }

    @Override
    public ServiceResult updateFamilyDetails(String token, List<FamilyMember> family)
            throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        if (family == null) return ServiceResult.fail("Family list is missing.");
        if (family.size() > MAX_FAMILY) return ServiceResult.fail("At most " + MAX_FAMILY + " family members are allowed.");

        // Rebuild every member from validated values instead of trusting the objects sent by the client
        List<FamilyMember> clean = new ArrayList<>();
        for (FamilyMember m : family) {
            if (m == null) return ServiceResult.fail("Invalid family member.");
            String name = m.getName() == null ? "" : m.getName().trim();
            String rel = m.getRelationship() == null ? "" : m.getRelationship().trim();
            String ic = m.getIcPassport() == null ? "" : m.getIcPassport().trim();
            if (!NAME.matcher(name).matches()) return ServiceResult.fail("Invalid family member name: '" + name + "'.");
            if (!NAME.matcher(rel).matches()) return ServiceResult.fail("Invalid relationship for " + name + ".");
            if (!ic.isEmpty() && !IC_PASSPORT.matcher(ic).matches()) return ServiceResult.fail("Invalid IC/Passport for " + name + ".");
            clean.add(new FamilyMember(name, rel, ic));
        }
        log(id, "updateFamilyDetails");
        return store.updateFamily(id, clean);
    }

    @Override
    public int getLeaveBalance(String token) throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        return store.getLeaveBalance(id);
    }

    @Override
    public ServiceResult applyLeave(String token, LeaveApplication application)
            throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        if (application == null) return ServiceResult.fail("Leave application is missing.");

        String type = null;
        for (String t : LeaveApplication.TYPES) {
            if (t.equalsIgnoreCase(application.getLeaveType() == null ? "" : application.getLeaveType().trim())) type = t;
        }
        if (type == null) return ServiceResult.fail("Leave type must be one of: " + String.join(", ", LeaveApplication.TYPES) + ".");

        LocalDate start = application.getStartDate();
        LocalDate end = application.getEndDate();
        if (start == null || end == null) return ServiceResult.fail("Start and end dates are required.");
        if (end.isBefore(start)) return ServiceResult.fail("End date cannot be before the start date.");
        if (start.isBefore(LocalDate.now())) return ServiceResult.fail("Start date cannot be in the past.");
        if (end.toEpochDay() - start.toEpochDay() + 1 > MAX_LEAVE_DAYS) return ServiceResult.fail("A single application cannot exceed " + MAX_LEAVE_DAYS + " days.");

        String reason = application.getReason() == null ? "" : application.getReason().trim();
        if (reason.length() > 200) return ServiceResult.fail("Reason is too long (max 200 characters).");

        log(id, "applyLeave " + start + " to " + end);
        // Build a fresh object: the server decides id, owner, status and date itself
        return store.applyLeave(id, new LeaveApplication(type, start, end, reason));
    }

    @Override
    public List<LeaveApplication> getLeaveStatus(String token) throws RemoteException, ServiceException {
        String id = sessions.requireEmployeeId(token);
        return store.getLeavesFor(id);
    }

    private static void log(String employeeId, String action) {
        System.out.println("[ACTION] " + employeeId + " " + action);
    }
}
