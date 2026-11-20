package hrm.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Shared login service. EVERY client (employee, HR, admin) logs in here and
 * gets a session token, which it passes to the other remote services.
 */
public interface AuthService extends Remote {
    String BINDING_NAME = "AuthService";

    /** Returns a session token, or throws ServiceException (wrong password, account locked). */
    String login(String employeeId, String password) throws RemoteException, ServiceException;

    void logout(String token) throws RemoteException;

    /** Role of the logged-in user; throws SessionExpiredException if the token is not valid. */
    Role getRole(String token) throws RemoteException, ServiceException;
}
