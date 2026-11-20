package hrm.server;

import hrm.common.AuthService;
import hrm.common.Employee;
import hrm.common.Role;
import hrm.common.ServiceException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Login/logout for all roles, with a simple lock-out after repeated wrong passwords. */
public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    private static final long serialVersionUID = 1L;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 60_000;

    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /** csf/ssf may be null (plain RMI) or SSL socket factories (secure RMI). */
    public AuthServiceImpl(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
    }

    @Override
    public String login(String employeeId, String password) throws RemoteException, ServiceException {
        if (employeeId == null || password == null || employeeId.trim().isEmpty()) {
            throw new ServiceException("Employee ID and password are required.");
        }
        String id = employeeId.trim();
        DataStore store = DataStore.getInstance();

        Long until = lockedUntil.get(id);
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                throw new ServiceException("Too many failed attempts. Please try again in a minute.");
            }
            lockedUntil.remove(id);
            failures.remove(id);
        }

        Employee e = store.authenticate(id, password);
        if (e == null) {
            if (store.exists(id)) {
                int n = failures.merge(id, 1, Integer::sum);
                if (n >= MAX_FAILURES) lockedUntil.put(id, System.currentTimeMillis() + LOCK_MS);
            }
            // same message for "unknown ID" and "wrong password" so IDs cannot be guessed
            throw new ServiceException("Invalid employee ID or password.");
        }
        failures.remove(id);
        System.out.println("[LOGIN ] " + id);
        return SessionManager.getInstance().createSession(id);
    }

    @Override
    public void logout(String token) throws RemoteException {
        SessionManager.getInstance().invalidate(token);
    }

    @Override
    public Role getRole(String token) throws RemoteException, ServiceException {
        return SessionManager.getInstance().getRole(token);
    }
}
