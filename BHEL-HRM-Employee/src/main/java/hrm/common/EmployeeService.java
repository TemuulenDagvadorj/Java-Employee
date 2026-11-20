package hrm.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;


public interface EmployeeService extends Remote {
    String BINDING_NAME = "EmployeeService";

    Employee getProfile(String token) throws RemoteException, ServiceException;

    ServiceResult updateProfile(String token, String firstName, String lastName, String icPassport)
            throws RemoteException, ServiceException;

    /** Replaces the employee's whole family list with the given list. */
    ServiceResult updateFamilyDetails(String token, List<FamilyMember> family)
            throws RemoteException, ServiceException;

    int getLeaveBalance(String token) throws RemoteException, ServiceException;

    ServiceResult applyLeave(String token, LeaveApplication application)
            throws RemoteException, ServiceException;

    /** All leave applications of the logged-in employee, with their current status. */
    List<LeaveApplication> getLeaveStatus(String token) throws RemoteException, ServiceException;
}
